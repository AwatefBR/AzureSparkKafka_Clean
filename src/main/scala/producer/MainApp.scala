package producer
import common.Config
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

object MainApp {

  def main(args: Array[String]): Unit = {
    require(
      args.nonEmpty, "Usage: MainApp [players|scoreboard]"
    )
    require(
      Set("players", "scoreboard").contains(args.head.toLowerCase),
      s"Mode invalide: ${args.head}. Usage: MainApp [players|scoreboard]"
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

    val batchSize = 1000
    val intervalSeconds = 1
    
    // Récupérer le dernier offset Kafka pour reprendre où on s'est arrêté
    val lastOffset = Utils.getLastOffset(tableName, Config.bootstrap)
    println(s"[Checkpoint] 📍 Dernier offset Kafka pour topic $tableName: $lastOffset")
    
    println(s"[SimStream] Envoi de $batchSize lignes toutes les $intervalSeconds secondes en boucle continue")
    if (lastOffset > 0) {
      println(s"[SimStream] ⚠️  Reprise depuis l'offset $lastOffset (${lastOffset} lignes déjà envoyées)")
    }
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
    
    println(s"[SimStream] $totalRows lignes chargées depuis $tableName")
    
    if (totalRows == 0) {    val dfIndexed = data.withColumn("rowId", monotonically_increasing_id()).cache()

      println(s"[SimStream] Aucune donnée dans la table $tableName, arrêt...")
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

        println(s"[SimStream] Lignes $cursor à ${cursor + batchSize - 1} envoyées (total: $totalRows)")
      }

      cursor += batchSize
      Thread.sleep(intervalSeconds * 1000)
    }
    
    println(s"[SimStream] ✅ Terminé : Toutes les $totalRows lignes ont été envoyées au topic Kafka '$tableName'")
  }
}