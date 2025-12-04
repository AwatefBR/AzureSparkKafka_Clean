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
        .option("dbtable", tableName)
        .option("user", Config.pgUser)
        .option("password", Config.pgPass)
        .load()

    // -----------------------------
    //   STREAMING SIMULÉ ICI
    // -----------------------------

    // → On ajoute un index à chaque ligne
    val dfIndexed = data.withColumn("rowId", monotonically_increasing_id())

    val totalRows = dfIndexed.count()
    val batchSize = 5
    var cursor = 0L

    println(s"[SimStream] Starting streaming simulation on table=$tableName")
    println(s"[SimStream] Total rows: $totalRows → sending $batchSize rows every 2 seconds")

    // → boucle streaming simulée
    while (cursor < totalRows) {

      // batch = lignes rowId ∈ [cursor ; cursor+batchSize[
      val batch = dfIndexed.filter(
        col("rowId") >= cursor &&
        col("rowId") < cursor + batchSize
      )

      val forKafka = batch.selectExpr(
        "CAST(rowId AS STRING) AS key",
        "to_json(struct(*)) AS value"
      )

      // envoi dans kafka
      forKafka.write
        .format("kafka")
        .option("kafka.bootstrap.servers", Config.bootstrap)
        .option("topic", tableName) // players → topic players, scoreboardplayers → topic scoreboardplayers
        .save()

      println(s"[SimStream] Sent rows $cursor to ${cursor + batchSize - 1}")

      cursor += batchSize
      Thread.sleep(2000) // ← 2 secondes entre chaque batch
    }

    println(s"[SimStream] Completed streaming for table=$tableName")
  }
}
