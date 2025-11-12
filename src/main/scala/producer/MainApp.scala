package producer
import common.Config


object MainApp {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Usage: MainApp [players|scoreboard|all]")
      sys.exit(1)
    }

    args(0).toLowerCase match {
      case "players" =>
        println("▶ Running PlayersProducer...")
        PlayersProducer.run()

      case "scoreboard" =>
        println("▶ Running ScoreboardProducer...")
        ScoreboardProducer.run()

      case "all" =>
        println("▶ Running both producers in parallel...")
        val threads = Seq(
          new Thread(() => PlayersProducer.run()),
          new Thread(() => ScoreboardProducer.run())
        )
        threads.foreach(_.start())
        threads.foreach(_.join())
        println("✅ All producers finished successfully.")

      case other =>
        println(s"❌ Unknown producer: $other")
        println("Usage: MainApp [players|scoreboard|all]")
    }
  }
}
