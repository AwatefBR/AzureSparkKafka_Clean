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

    // ---- LECTURE DE LA TABLE (statique) ----
    val data: DataFrame =
      spark.read.format("jdbc")
        .option("url", Config.pgUrl)
        .option("driver", "org.postgresql.Driver")
        .option("dbtable", tableName)
        .option("user", Config.pgUser)
        .option("password", Config.pgPass)
        .load()

    // -----------------------------
    //   STREAMING CONTINU SIMULÉ
    // -----------------------------
    
    val batchSize = 5
    val intervalSeconds = 2
    
    println(s"[SimStream] Starting continuous streaming simulation on table=$tableName")
    println(s"[SimStream] Sending $batchSize rows every $intervalSeconds seconds in a continuous loop")
    println(s"[SimStream] Press Ctrl+C to stop")

    // → Boucle infinie pour streaming continu
    var cycle = 0
    while (true) {
      try {
        // Relire la table à chaque cycle (pour simuler de nouvelles données)
        val data: DataFrame = spark.read.format("jdbc")
          .option("url", Config.pgUrl)
          .option("driver", "org.postgresql.Driver")
          .option("dbtable", tableName)
          .option("user", Config.pgUser)
          .option("password", Config.pgPass)
          .load()

        val dfIndexed = data.withColumn("rowId", monotonically_increasing_id())
        val totalRows = dfIndexed.count()
        
        if (totalRows == 0) {
          println(s"[SimStream] Cycle $cycle: No data in table $tableName, waiting...")
          Thread.sleep(intervalSeconds * 1000)
          cycle += 1
        } else {
          // Envoyer les données par batch
          var cursor = 0L
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

              // envoi dans kafka
              forKafka.write
                .format("kafka")
                .option("kafka.bootstrap.servers", Config.bootstrap)
                .option("topic", tableName)
                .save()

              println(s"[SimStream] Cycle $cycle: Sent rows $cursor to ${cursor + batchSize - 1} (total: $totalRows)")
            }

            cursor += batchSize
            Thread.sleep(intervalSeconds * 1000)
          }
          
          println(s"[SimStream] Cycle $cycle completed: Processed $totalRows rows. Restarting cycle...")
          cycle += 1
          
          // Pause entre les cycles pour éviter de surcharger
          Thread.sleep(5000)
        }
      } catch {
        case e: Exception =>
          println(s"[SimStream] ERROR in cycle $cycle: ${e.getMessage}")
          e.printStackTrace()
          println(s"[SimStream] Retrying in 10 seconds...")
          Thread.sleep(10000)
      }
    }
  }
}
