package producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.util.Properties
import java.sql.DriverManager
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

  def main(args: Array[String]): Unit = {

    // ----------- ENV VARS -----------
    val bootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP", "kafka:9092")
    val pgHost = sys.env("POSTGRES_HOST")
    val pgDb = sys.env("POSTGRES_DB")
    val pgUser = sys.env("POSTGRES_USER")
    val pgPass = sys.env("POSTGRES_PASSWORD")

    // ----------- KAFKA PROD -----------
    val props = new Properties()
    props.put("bootstrap.servers", bootstrap)
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    // ----------- POSTGRES JDBC -----------
    val url = s"jdbc:postgresql://$pgHost:5432/$pgDb"
    Class.forName("org.postgresql.Driver")
    val conn = DriverManager.getConnection(url, pgUser, pgPass)

    println(s"[Producer:Scoreboard]  Connected to Postgres at $pgHost/$pgDb")

    val query =
      """
        SELECT *
        FROM scoreboard;
      """

    val rs = conn.createStatement().executeQuery(query)

    var count = 0

    while (rs.next()) {
      val s = Scoreboard(
        rs.getString("overviewpage"), rs.getString("name"), rs.getString("link"),
        rs.getString("champion"), rs.getString("kills"), rs.getString("deaths"),
        rs.getString("assists"), rs.getString("gold"), rs.getString("cs"),
        rs.getString("damagetochampions"), rs.getString("items"), rs.getString("teamkills"),
        rs.getString("teamgold"), rs.getString("team"), rs.getString("teamvs"),
        rs.getString("time"), rs.getString("playerwin"), rs.getString("datetime_utc"),
        rs.getString("dst"), rs.getString("tournament"), rs.getString("role"),
        rs.getString("gameid"), rs.getString("matchid"), rs.getString("gameteamid"),
        rs.getString("gameroleid"), rs.getString("statspage")
      )

      val key = s.gameid + ":" + s.name
      producer.send(new ProducerRecord[String, String]("scoreboard", key, write(s)))
      count += 1
    }

    println(s"[Producer:Scoreboard]  Finished → $count rows sent to Kafka topic `scoreboard`")

    producer.close()
    conn.close()
  }
}
