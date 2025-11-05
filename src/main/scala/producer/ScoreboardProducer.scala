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

  private def get(f: ujson.Value, key: String): String =
    f.obj.get(key).flatMap(_.strOpt).getOrElse("")

  def main(args: Array[String]): Unit = {

    val bootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP", "kafka:9092")

    val props = new Properties()
    props.put("bootstrap.servers", bootstrap)
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    val apiUrl =
      "https://lol.fandom.com/api.php?action=runquery&query=ScoreboardPlayers&format=json"

    while (true) {
      println("[Producer:Scoreboard] Fetching data…")

      val response = requests.get(
        apiUrl,
        readTimeout = 30000,
        headers = Map("User-Agent" -> "Mozilla/5.0") // Anti rate-limit
      ).text()

      val json = ujson.read(response)

      // ✅ Si rate-limit, json = {"error": ...}
      if (json.obj.contains("error")) {
        println("[Producer:Scoreboard] ⏳ Rate Limit → Sleep 5s")
        Thread.sleep(5000)
      } else if (!json.obj.contains("cargoquery")) {
        println("[Producer:Scoreboard] ⚠️ No cargoquery field → Sleep 5s")
        Thread.sleep(5000)
      } else {
        val data = json("result")("results").arr

        data.foreach { entry =>
          val f = entry("title")
          val s = Scoreboard(
            overviewpage = get(f, "OverviewPage"),
            name = get(f, "Name"),
            link = get(f, "Link"),
            champion = get(f, "Champion"),
            kills = get(f, "Kills"),
            deaths = get(f, "Deaths"),
            assists = get(f, "Assists"),
            gold = get(f, "Gold"),
            cs = get(f, "CS"),
            damagetochampions = get(f, "DamageToChampions"),
            items = get(f, "Items"),
            teamkills = get(f, "TeamKills"),
            teamgold = get(f, "TeamGold"),
            team = get(f, "Team"),
            teamvs = get(f, "TeamVs"),
            time = get(f, "Time"),
            playerwin = get(f, "PlayerWin"),
            datetime_utc = get(f, "DateTime_UTC"),
            dst = get(f, "DST"),
            tournament = get(f, "Tournament"),
            role = get(f, "Role"),
            uniqueline = get(f, "UniqueLine"),
            uniquelinevs = get(f, "UniqueLineVs"),
            gameid = get(f, "GameId"),
            matchid = get(f, "MatchId"),
            gameteamid = get(f, "GameTeamId"),
            gameroleid = get(f, "GameRoleId"),
            gameroleidvs = get(f, "GameRoleIdVs"),
            statspage = get(f, "StatsPage")
          )

          val value = write(s)
          val key = s.gameid + ":" + s.name
          producer.send(new ProducerRecord[String, String]("scoreboard", key, value))
        }

        println("[Producer:Scoreboard] ✅ Sent batch. Sleeping 5s…")
        Thread.sleep(5000)
      }
    }
  }
}
