package producer
import common.Config
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.util.Properties
import scala.collection.JavaConverters._

object MainApp {

  // Fonction pour obtenir le dernier offset du topic Kafka
  def getLastOffset(topic: String, bootstrap: String): Long = {
    try {
      val props = new Properties()
      props.put("bootstrap.servers", bootstrap)
      props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      props.put("group.id", s"producer-checkpoint-$topic")
      
      val consumer = new KafkaConsumer[String, String](props)
      val partition = new TopicPartition(topic, 0)
      consumer.assign(java.util.Collections.singletonList(partition))
      consumer.seekToEnd(java.util.Collections.singletonList(partition))
      val lastOffset = consumer.position(partition)
      consumer.close()
      
      lastOffset
    } catch {
      case e: Exception =>
        println(s"[Checkpoint] ⚠️  Erreur lors de la lecture de l'offset Kafka: ${e.getMessage}")
        println(s"[Checkpoint] Repartant depuis 0...")
        0L
    }
  }

  def main(args: Array[String]): Unit = {
    require(
      args.nonEmpty, "Usage: MainApp [players|scoreboard]"
    )
    require(
      Set("players", "scoreboard").contains(args.head.toLowerCase),
      s"Invalid mode: ${args.head}. Usage: MainApp [players|scoreboard]"
    )

    val spark = SparkSession.builder()
      .appName("ProducerMainApp")
      .master("local[*]") 
      .getOrCreate()

    val mode = args.head.toLowerCase

    // ---- CHOIX TABLE ----
    val tableName =
      if (mode == "players")
        "players"
      else
        "scoreboardplayers"

    // -----------------------------
    //   STREAMING CONTINU SIMULÉ AVEC CHECKPOINT
    // -----------------------------
    
    val batchSize = 1000
    val intervalSeconds = 1
    
    // Récupérer le dernier offset Kafka pour reprendre où on s'est arrêté
    val lastOffset = getLastOffset(tableName, Config.bootstrap)
    println(s"[Checkpoint] 📍 Dernier offset Kafka pour topic $tableName: $lastOffset")
    
    println(s"[SimStream] Starting continuous streaming simulation on table=$tableName")
    println(s"[SimStream] Sending $batchSize rows every $intervalSeconds seconds in a continuous loop")
    if (lastOffset > 0) {
      println(s"[SimStream] ⚠️  Reprise depuis l'offset $lastOffset (${lastOffset} lignes déjà envoyées)")
    }
    println(s"[SimStream] Press Ctrl+C to stop")

    // Charger la table UNE SEULE FOIS au démarrage
    val data: DataFrame = spark.read.format("jdbc")
      .option("url", Config.pgUrl)
      .option("driver", "org.postgresql.Driver")
      .option("dbtable", tableName)
      .option("user", Config.pgUser)
      .option("password", Config.pgPass)
      .load()

    val dfIndexed = data.withColumn("rowId", monotonically_increasing_id()).cache()
    val totalRows = dfIndexed.count()
    
    println(s"[SimStream] Loaded $totalRows rows from $tableName")
    
    if (totalRows == 0) {
      println(s"[SimStream] No data in table $tableName, exiting...")
      return
    }
    
    // Calculer le point de départ : reprendre depuis le checkpoint si disponible
    val startCursor = if (lastOffset > 0) {
      val checkpointRow = math.min(lastOffset, totalRows)
      println(s"[Checkpoint] 🔄 Reprise depuis la ligne $checkpointRow (offset Kafka: $lastOffset)")
      checkpointRow
    } else {
      0L
    }
    
    // Envoyer les données par batch (un seul cycle complet)
    var cursor = startCursor
    while (cursor < totalRows) {
      val batch = dfIndexed.filter(
        col("rowId") >= cursor &&
        col("rowId") < cursor + batchSize
      )

      if (!batch.isEmpty) {
        val forKafka = batch.selectExpr(
          "CAST(rowId AS STRING) AS key",
          "to_json(struct(*)) AS value"
        )

        // Envoi dans Kafka
        forKafka.write
          .format("kafka")
          .option("kafka.bootstrap.servers", Config.bootstrap)
          .option("topic", tableName)
          .save()

        println(s"[SimStream] Sent rows $cursor to ${cursor + batchSize - 1} (total: $totalRows)")
      }

      cursor += batchSize
      Thread.sleep(intervalSeconds * 1000)
    }
    
    println(s"[SimStream] ✅ Completed: All $totalRows rows sent to Kafka topic '$tableName'")
  }
}