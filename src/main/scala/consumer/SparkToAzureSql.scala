package consumer

import common.Config
import consumer.schemas.{PlayerSchema, ScoreboardSchema}
import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger

object SparkToAzureSql {

  // Fonction simple d'écriture dans Azure SQL
  def writeToAzure(tableName: String)(batchDF: DataFrame, batchId: Long): Unit = {
    try {
      if (batchDF == null || batchDF.isEmpty) {
        println(s"[Azure] Batch $batchId vide → ignoré")
        return
      }

      val count = batchDF.count()
      println(s"[Azure] Batch $batchId : écriture de $count lignes → $tableName")

      batchDF.write
        .format("jdbc")
        .option("url", Config.azureJdbcUrl)
        .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
        .option("dbtable", tableName)
        .option("user", Config.azureUser)
        .option("password", Config.azurePassword)
        .mode("append")
        .save()

      println(s"[Azure] ✅ Batch $batchId : $count lignes écrites avec succès")
    } catch {
      case e: Exception =>
        println(s"[Azure] ❌ Batch $batchId : erreur → ${e.getMessage}")
        e.printStackTrace()
    }
  }

  def main(args: Array[String]): Unit = {
    val bootstrap = Config.bootstrap
    
    val mode = args.headOption
      .getOrElse("scoreboard")
    
    val (topic, schema, tableName, checkpointPath) = mode.toLowerCase match {
      case "players" => 
        ("players", PlayerSchema.schema, "dbo.Players", "/checkpoints/players")
      case "scoreboard" | _ => 
        ("scoreboardplayers", ScoreboardSchema.schema, "dbo.Scoreboard", "/checkpoints/scoreboardplayers")
    }

    println(s"[Consumer] 🚀 Démarrage du consumer sur topic: $topic → table: $tableName")

    val spark = SparkSession.builder()
      .appName(s"KafkaToAzureSQL-$mode")
      .master(sys.env.getOrElse("SPARK_MASTER", "spark://spark-master:7077"))
      .config("spark.executor.cores", "2")
      .config("spark.executor.memory", "1g")
      .config("spark.cores.max", "4") 
      .getOrCreate()
    // Lecture du stream Kafka
    val stream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrap)
      .option("subscribe", topic)
      .option("startingOffsets", "earliest")
      .option("maxOffsetsPerTrigger", "1000")  // Maximum 1000 messages par batch
      .load()

    // Parsing JSON avec le schéma approprié
    val parsed = stream
      .selectExpr("CAST(value AS STRING) AS json")
      .select(from_json(col("json"), schema).as("data"))
      .select("data.*")
      .dropDuplicates(
        if (mode.toLowerCase == "scoreboard") "uniqueline" else "overviewpage"
      ) // Déduplication : uniqueline pour scoreboard, id pour players
      
    // Écriture dans Azure SQL
    val query = parsed.writeStream
      .trigger(Trigger.ProcessingTime("1 seconds"))
      .foreachBatch((batchDF: DataFrame, batchId: Long) => writeToAzure(tableName)(batchDF, batchId))
      .outputMode("append")
      .option("checkpointLocation", checkpointPath)
      .start()

    println("[Consumer] ✅ Consumer lancé. En attente de messages Kafka...")
    spark.streams.awaitAnyTermination()
  }
}
