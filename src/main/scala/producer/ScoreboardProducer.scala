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
    role: String, gameid: String, matchid: String, gameteamid: String,
    gameroleid: String, statspage: String
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

    val fields =
      "SP.OverviewPage,SP.Name,SP.Link,SP.Champion,SP.Kills,SP.Deaths,SP.Assists,SP.Gold,SP.CS,SP.DamageToChampions," +
      "SP.Items,SP.TeamKills,SP.TeamGold,SP.Team,SP.TeamVs,SP.Time,SP.PlayerWin,SP.DateTime_UTC,SP.DST,SP.Tournament," +
      "SP.Role,SP.GameId,SP.MatchId,SP.GameTeamId,SP.GameRoleId,SP.StatsPage"

    val where = "_DATE(SP.DateTime_UTC) >= \"2024-01-01\""
    val whereEncoded = java.net.URLEncoder.encode(where, "UTF-8")

    val batchSize = 100
    var offset = 0

    while (true) {

      val url =
        s"https://lol.fandom.com/api.php?action=cargoquery" +
        s"&tables=ScoreboardPlayers=SP" +
        s"&fields=$fields" +
        s"&where=$whereEncoded" +
        s"&limit=$batchSize" +
        s"&offset=$offset" +
        s"&format=json"

      println(s"[Producer:Scoreboard] Fetching offset=$offset …")

      val response = requests.get(
        url,
        headers = Map("User-Agent" -> "LeaguepediaSparkCollector/1.0"),
        readTimeout = 30000
      ).text()

      val json = ujson.read(response)

      if (!json.obj.contains("cargoquery")) {
        println("[Producer:Scoreboard] ⏳ Rate-limit or invalid response → waiting 30s…")
        Thread.sleep(30000)
      }
      else {
        val batch = json("cargoquery").arr

        if (batch.isEmpty) {
          println("[Producer:Scoreboard] ✅ Finished → restarting in 2 minutes")
          offset = 0
          Thread.sleep(120000)
        } else {
          batch.foreach { row =>
            val f = row("title")

            val s = Scoreboard(
              get(f,"OverviewPage"), get(f,"Name"), get(f,"Link"), get(f,"Champion"),
              get(f,"Kills"), get(f,"Deaths"), get(f,"Assists"), get(f,"Gold"),
              get(f,"CS"), get(f,"DamageToChampions"), get(f,"Items"), get(f,"TeamKills"),
              get(f,"TeamGold"), get(f,"Team"), get(f,"TeamVs"), get(f,"Time"),
              get(f,"PlayerWin"), get(f,"DateTime_UTC"), get(f,"DST"), get(f,"Tournament"),
              get(f,"Role"), get(f,"GameId"), get(f,"MatchId"), get(f,"GameTeamId"),
              get(f,"GameRoleId"), get(f,"StatsPage")
            )

            val key = s.gameid + ":" + s.name
            producer.send(new ProducerRecord[String, String]("scoreboard", key, write(s)))
          }

          println(s"[Producer:Scoreboard] ✅ Sent ${batch.size} rows")
          offset += batchSize
          Thread.sleep(10000)
        }
      }
    }
  }
}
