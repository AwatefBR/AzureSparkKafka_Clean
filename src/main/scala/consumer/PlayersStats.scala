package consumer

import common.Config
import consumer.schemas.ScoreboardSchema
import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger

object PlayersStats {

  def main(args: Array[String]): Unit = {

    println("[PlayersStats] 🚀 Démarrage du job PlayersStats (streaming)")

    // 1️⃣ Spark Session
    val spark = SparkSession.builder()
      .appName("PlayersStatsStreaming")
      .master(sys.env.getOrElse("SPARK_MASTER", "spark://spark-master:7077"))
      .config("spark.executor.cores", "2")
      .config("spark.executor.memory", "2g")
      .config("spark.cores.max", "4")
      .getOrCreate()

    import spark.implicits._

    // 2️⃣ Lecture Kafka (même topic que le consumer scoreboard)
    val stream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", Config.bootstrap)
      .option("subscribe", "scoreboardplayers")
      .option("startingOffsets", "latest")
      .option("maxOffsetsPerTrigger", "1000")
      .load()

    // 3️⃣ Parsing JSON
    val parsed = stream
      .selectExpr("CAST(value AS STRING) AS json")
      .select(from_json(col("json"), ScoreboardSchema.schema).as("data"))
      .select("data.*")

    // 4️⃣ Event time + renommage clé joueur (link → player_id)
    val withEventTime = parsed
      .withColumn(
        "event_time",
        coalesce(
          to_timestamp(col("datetime_utc")),
          current_timestamp()
        )
      )
      .withColumnRenamed("link", "player_id")

    // 5️⃣ CAST NUMÉRIQUE
    val numeric = withEventTime
      .withColumn("kills_i", col("kills").cast("double"))
      .withColumn("deaths_i", col("deaths").cast("double"))
      .withColumn("assists_i", col("assists").cast("double"))

    // 6️⃣ Calcul KDA par game
    val enriched = numeric
      .withColumn(
        "kda",
        (col("kills_i") + col("assists_i")) /
          greatest(col("deaths_i"), lit(1.0))
      )

    // 7️⃣ Agrégations PlayerStats par player_id
    val playerStats = enriched
      .withWatermark("event_time", "10 minutes")
      .groupBy("player_id")
      .agg(
        // Moyennes
        avg("kills_i").as("avg_kills"),
        avg("deaths_i").as("avg_deaths"),
        avg("assists_i").as("avg_assists"),

        // Cumuls
        sum("kills_i").as("sum_kills"),
        sum("deaths_i").as("sum_deaths"),
        sum("assists_i").as("sum_assists"),

        // KDA moyen par game
        avg("kda").as("avg_kda"),

        // Nombre de parties
        count("*").as("games_played")
      )
      // KDA global depuis les sommes
      .withColumn(
        "kda_from_sums",
        (col("sum_kills") + col("sum_assists")) /
          greatest(col("sum_deaths"), lit(1.0))
      )
      .withColumn("updated_at", current_timestamp())

    // 8️⃣ Écriture Azure SQL
    val query = playerStats.writeStream
      .outputMode("update")
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        if (!batchDF.isEmpty) {
          println(s"[PlayersStats] 📊 Batch $batchId → écriture vers Azure SQL")
          Utils.writeToAzure("dbo.PlayerStats", useUpsert = true)(batchDF, batchId)
          println(s"[PlayersStats] ✅ Batch $batchId terminé")
        } else {
          println(s"[PlayersStats] Batch $batchId vide")
        }
      }
      .option("checkpointLocation", "/checkpoints/playerstats")
      .start()

    println("[PlayersStats] ✅ Job lancé")
    spark.streams.awaitAnyTermination()
  }
}
