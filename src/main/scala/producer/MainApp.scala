package producer
import common.Config


object MainApp {

  def main(args: Array[String]): Unit = {
    require(
        args.nonEmpty, "Usage: MainApp [players|scoreboard|all]",
        args.head.toLowerCase.inSet("players", "scoreboard", "all")
    )

    val spark = SparkSession.builder()
      .appName("ProducerMainApp")
      .getOrCreate()

val mode = args.head.toLowerCase

val tableName = 
    if (mode == "player")
        "tableNameForPlayers"
    else
        "tableNameForScores"

    val data: DataFrame = 
        spark.read.format("jdbc")
        .option("url", "jdbc:postgresql://host/database")
        .option("dbtable", tableName)
        .option("user", "username")
        .option("password", "password")
        .load()

        // todo un truc ici

        data.write
        .format("kafka")
        .option("kafka.bootstrap.servers", "host1:port1,host2:port2")
        .save()

    args(0).toLowerCase match {
      case "players" =>
        println("Running PlayersProducer...")
        PlayersProducer.run()

      case "scoreboard" =>
        println("Running ScoreboardProducer...")
        ScoreboardProducer.run()

      case "all" =>
        println("Running both producers in parallel...")
        val threads = Seq(
          new Thread(() => PlayersProducer.run()),
          new Thread(() => ScoreboardProducer.run())
        )
        threads.foreach(_.start())
        threads.foreach(_.join())
        println("All producers finished successfully.")

      case other =>
        println(s"Unknown producer: $other")
        println("Usage: MainApp [players|scoreboard|all]")
    }
  }
}
