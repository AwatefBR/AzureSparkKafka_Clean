package producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.util.Properties
import upickle.default._

object ScoreboardProducer {

  case class Scoreboard(
    overviewpage: String, name: String, link: String, champion: String, kills: String,
    deaths: String, assists: String, gold: String, cs: String, damagetochampions: String,
    items: String, teamkills: String, teamgold: String, team: String, teamvs: String,
    time: String, playerwin: String, datetime_utc: String, dst: String, tournament: String,
    role: String, uniqueline: String, uniquelinevs: String, gameid: String, matchid: String,
    gameteamid: String, gameroleid: String, gameroleidvs: String, statspage: String
  )

  implicit val rwScoreboard: ReadWriter[Scoreboard] = macroRW

  def main(args: Array[String]): Unit = {

    val props = new Properties()
    props.put("bootstrap.servers", "kafka:9092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    while (true) {
      println("[Producer:Scoreboard] Fetching data...")

      val apiUrl = "https://lol.fandom.com/api.php?action=cargoquery&tables=ScoreboardPlayers&limit=200&format=json"
      val json = requests.get(apiUrl).text()

      val data = ujson.read(json)("cargoquery").arr

      data.foreach { entry =>
        val fields = entry("title")
        val s = Scoreboard(
          overviewpage = fields("OverviewPage").str,
          name = fields("Name").str,
          link = fields("Link").str,
          champion = fields("Champion").str,
          kills = fields("Kills").str,
          deaths = fields("Deaths").str,
          assists = fields("Assists").str,
          gold = fields("Gold").str,
          cs = fields("CS").str,
          damagetochampions = fields("DamageToChampions").str,
          items = fields("Items").str,
          teamkills = fields("TeamKills").str,
          teamgold = fields("TeamGold").str,
          team = fields("Team").str,
          teamvs = fields("TeamVs").str,
          time = fields("Time").str,
          playerwin = fields("PlayerWin").str,
          datetime_utc = fields("DateTime_UTC").str,
          dst = fields("DST").str,
          tournament = fields("Tournament").str,
          role = fields("Role").str,
          uniqueline = fields("UniqueLine").str,
          uniquelinevs = fields("UniqueLineVs").str,
          gameid = fields("GameId").str,
          matchid = fields("MatchId").str,
          gameteamid = fields("GameTeamId").str,
          gameroleid = fields("GameRoleId").str,
          gameroleidvs = fields("GameRoleIdVs").str,
          statspage = fields("StatsPage").str
        )

        val jsonValue = write(s)
        producer.send(new ProducerRecord[String, String]("scoreboard", s.gameid, jsonValue))
      }

      println("[Producer:Scoreboard] ✅ Sent batch. Sleeping…")
      Thread.sleep(3000)
    }
  }
}
