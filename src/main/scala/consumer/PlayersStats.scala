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

    // 2️⃣ Lecture Kafka
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

    // 4️⃣ Event time + renommage joueur
    val withEventTime = parsed
      .withColumn(
        "event_time",
        coalesce(
          to_timestamp(col("datetime_utc")),
          current_timestamp()
        )
      )
      .withColumnRenamed("name", "playerName")

    // 5️⃣ CAST NUMÉRIQUE (POINT CLÉ)
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

    // 7️⃣ Agrégations PlayerStats
    val playerStats = enriched
      .withWatermark("event_time", "10 minutes")
      .groupBy("playerName")
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

    // Variables de monitoring (partagées entre batches)
    var totalMatchesProcessed = 0L
    var totalPlayersSeen = scala.collection.mutable.Set[String]()

    // 8️⃣ Écriture Azure SQL avec monitoring
    val query = playerStats.writeStream
      .outputMode("update")
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        if (!batchDF.isEmpty) {
          val playerCount = batchDF.count()
          val playersInBatch = batchDF.select("playerName").collect().map(_.getString(0)).toSet
          
          // Estimer les matchs traités depuis games_played (somme des parties des joueurs du batch)
          val matchesRow = batchDF.agg(sum("games_played").as("total")).collect()(0)
          val matchesInBatch = try {
            val value = matchesRow.getAs[java.lang.Long]("total")
            if (value != null) value.longValue() else 0L
          } catch {
            case _: Exception => 0L
          }
          totalMatchesProcessed += matchesInBatch
          totalPlayersSeen ++= playersInBatch
          
          println(s"[PlayersStats] 📊 Batch $batchId →")
          println(s"  - Joueurs dans le batch: $playerCount")
          println(s"  - Matchs dans le batch: $matchesInBatch")
          println(s"  - Joueurs uniques totaux: ${totalPlayersSeen.size}")
          println(s"  - Matchs traités totaux: $totalMatchesProcessed")
          println(s"  - Écriture vers Azure SQL (MERGE/UPSERT)...")
          
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
