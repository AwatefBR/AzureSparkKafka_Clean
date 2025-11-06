package producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.util.Properties
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

  private def get(f: ujson.Value, key: String): String =
    f.obj.get(key).flatMap(_.strOpt).getOrElse("")

  def main(args: Array[String]): Unit = {

    val bootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP", "kafka:9092")

    val props = new Properties()
    props.put("bootstrap.servers", bootstrap)
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)

    val fieldsRaw =
      "ID,OverviewPage,Player,Image,Name,NativeName,NameAlphabet,NameFull," +
      "Country,Nationality,NationalityPrimary,Age,Birthdate,DeathDate,ResidencyFormer," +
      "Team,Team2,CurrentTeams,TeamSystem,Team2System,Residency,Role,FavChamps"

    val fieldsParam = java.net.URLEncoder.encode(fieldsRaw, "UTF-8")

    val batchSize = 500
    var offset = 0

    var keepGoing = true

    while (keepGoing) {

      val url =
        s"https://lol.fandom.com/api.php?action=cargoquery" +
        s"&tables=Players" +
        s"&fields=$fieldsParam" +
        s"&limit=$batchSize" +
        s"&offset=$offset" +
        s"&format=json"

      println(s"[Producer:Players] Fetching offset=$offset …")

      val text =
        try requests.get(
          url,
          headers = Map("User-Agent" -> "LeaguepediaDataCollector/1.0 (contact: youremail@example.com)"),
          readTimeout = 30000
        ).text()
        catch {
          case e: requests.RequestFailedException =>
            println(s"[Producer:Players] HTTP ${e.response.statusCode} → waiting 30s")
            Thread.sleep(30000)
            // retry same offset
            null
        }

      if (text == null) {
        // retry with same offset
      } else {
        val json = ujson.read(text)

        if (!json.obj.contains("cargoquery")) {
          println("[Producer:Players] ⏳ Rate-limited → waiting 30s…")
          Thread.sleep(30000)
        } else {
          val batch = json("cargoquery").arr

          if (batch.isEmpty) {
            println("✅ Finished all ~19,956 players. Stopping.")
            keepGoing = false
          } else {
            batch.foreach { row =>
              val f = row("title")
              val p = Player(
                get(f,"ID"), get(f,"OverviewPage"), get(f,"Player"), get(f,"Image"),
                get(f,"Name"), get(f,"NativeName"), get(f,"NameAlphabet"), get(f,"NameFull"),
                get(f,"Country"), get(f,"Nationality"), get(f,"NationalityPrimary"),
                get(f,"Age"), get(f,"Birthdate"), get(f,"DeathDate"), get(f,"ResidencyFormer"),
                get(f,"Team"), get(f,"Team2"), get(f,"CurrentTeams"), get(f,"TeamSystem"),
                get(f,"Team2System"), get(f,"Residency"), get(f,"Role"), get(f,"FavChamps")
              )
              producer.send(new ProducerRecord[String, String]("players", p.id, write(p)))
            }

            println(s"[Producer:Players] ✅ Sent ${batch.size} rows")
            offset += batchSize
            Thread.sleep(2000) // small cooldown
          }
        }
      }
    }

    println("🚀 All players inserted, closing producer.")
    producer.close()
  }
}
