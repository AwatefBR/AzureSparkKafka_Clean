// pas de classe dans object sparer package : class specifique ave function predefinie. ici pas necessaire (à definir fichier a part).
// utilisation key vault pour credentials conn 
// spark core pour les produceurs 
// boucle while --> tempo (simulation tps reel)
package producer
import org.apache.kafka.clients.producer.ProducerRecord

import common.Config
import java.util.Properties
import java.sql.DriverManager
import upickle.default._

object PlayersProducer {


  implicit val rwPlayer: ReadWriter[Player] = macroRW

  def run(): Unit = {
    val conn = Config.pgConnection
    val producer = Config.kafkaProducer

println(s"[Producer:Players] Connected to Postgres at ${Config.pgHost}/${Config.pgDb}")

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
      println(s"[Producer:Players] Producing player id=${p.id}, name=${p.name}")
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
