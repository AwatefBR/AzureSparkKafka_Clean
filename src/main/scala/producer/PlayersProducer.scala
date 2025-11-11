package producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.util.Properties
import java.sql.DriverManager
import upickle.default._

object PlayersProducer {

  case class Player(
    id: String, overviewpage: String, player: String, image: String, name: String,
    nativename: String, namealphabet: String, namefull: String, country: String,
    nationality: String, nationalityprimary: String, age: String, birthdate: String,
    deathdate: String, residencyformer: String, team: String, team2: String,
    currentteams: String, teamsystem: String, team2system: String, residency: String,
    role: String, favchamps: String
  )
  implicit val rwPlayer: ReadWriter[Player] = macroRW

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

    println(s"[Producer:Players] Connected to Postgres at $pgHost/$pgDb")

    val query =
      """
        SELECT *
        FROM players;
      """

    val rs = conn.createStatement().executeQuery(query)

    var count = 0
    while (rs.next()) {
      val p = Player(
        rs.getString("id"), rs.getString("overviewpage"), rs.getString("player"),
        rs.getString("image"), rs.getString("name"), rs.getString("nativename"),
        rs.getString("namealphabet"), rs.getString("namefull"), rs.getString("country"),
        rs.getString("nationality"), rs.getString("nationalityprimary"),
        rs.getString("age"), rs.getString("birthdate"), rs.getString("deathdate"),
        rs.getString("residencyformer"), rs.getString("team"), rs.getString("team2"),
        rs.getString("currentteams"), rs.getString("teamsystem"),
        rs.getString("team2system"), rs.getString("residency"),
        rs.getString("role"), rs.getString("favchamps")
      )

      producer.send(new ProducerRecord[String, String]("players", p.id, write(p)))
      count += 1
    }

    if (count == 0) {
      println("[Producer:Players] No data found in table `players`. Waiting gracefully (no crash).")
      // On attend un peu pour éviter le restart immédiat du conteneur
      Thread.sleep(30000)
    } else {
      println(s"[Producer:Players] Finished → $count rows sent to Kafka topic `players`")
    }

    producer.close()
    conn.close()
  }
}
