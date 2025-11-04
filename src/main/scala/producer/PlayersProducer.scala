package producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.util.Properties
import upickle.default._
import scala.concurrent.duration._
import scala.util.Try

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

    // Kafka config
    val props = new Properties()
    props.put("bootstrap.servers", "kafka:9092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    while (true) {
      println("[Producer:Players] Fetching data...")

      val apiUrl = "https://lol.fandom.com/api.php?action=cargoquery&tables=Players&limit=200&format=json"
      val json = requests.get(apiUrl).text()

      val data = ujson.read(json)("cargoquery").arr

      data.foreach { entry =>
        val fields = entry("title")
        val p = Player(
          id = fields("ID").str,
          overviewpage = fields("OverviewPage").str,
          player = fields("Player").str,
          image = fields("Image").str,
          name = fields("Name").str,
          nativename = fields("NativeName").str,
          namealphabet = fields("NameAlphabet").str,
          namefull = fields("NameFull").str,
          country = fields("Country").str,
          nationality = fields("Nationality").str,
          nationalityprimary = fields("NationalityPrimary").str,
          age = fields("Age").str,
          birthdate = fields("Birthdate").str,
          deathdate = fields("DeathDate").str,
          residencyformer = fields("ResidencyFormer").str,
          team = fields("Team").str,
          team2 = fields("Team2").str,
          currentteams = fields("CurrentTeams").str,
          teamsystem = fields("TeamSystem").str,
          team2system = fields("Team2System").str,
          residency = fields("Residency").str,
          role = fields("Role").str,
          favchamps = fields("FavChamps").str
        )

        val jsonValue = write(p)
        producer.send(new ProducerRecord[String, String]("players", p.id, jsonValue))
      }

      println("[Producer:Players] ✅ Sent batch. Sleeping…")
      Thread.sleep(3000)
    }
  }
}
