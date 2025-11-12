  package producer
  import common.Config

  import org.apache.kafka.clients.producer.ProducerRecord
  import upickle.default._

  object ScoreboardProducer {

    // ----- Sérialisation JSON -----
    implicit val rwScoreboard: ReadWriter[Scoreboard] = macroRW

    def run(): Unit = {

      // ----- Connexions centralisées -----
      val conn = Config.pgConnection
      val producer = Config.kafkaProducer

      println(s"[Producer:Scoreboard] Connected to Postgres at ${Config.pgHost}/${Config.pgDb}")

      // ----- Requête SQL -----
      val query =
        """
          SELECT *
          FROM scoreboardplayers;
        """

      val rs = conn.createStatement().executeQuery(query)

      var count = 0

      while (rs.next()) {
        val s = Scoreboard(
          rs.getInt("id"),
          rs.getString("overviewpage"),
          rs.getString("name"),
          rs.getString("link"),
          rs.getString("champion"),
          rs.getString("kills"),
          rs.getString("deaths"),
          rs.getString("assists"),
          rs.getString("gold"),
          rs.getString("cs"),
          rs.getString("damagetochampions"),
          rs.getString("items"),
          rs.getString("teamkills"),
          rs.getString("teamgold"),
          rs.getString("team"),
          rs.getString("teamvs"),
          rs.getString("time"),
          rs.getString("playerwin"),
          rs.getString("datetime_utc"),
          rs.getString("dst"),
          rs.getString("tournament"),
          rs.getString("role"),
          rs.getString("gameid"),
          rs.getString("matchid"),
          rs.getString("gameteamid"),
          rs.getString("gameroleid"),
          rs.getString("statspage")
        )

        // ----- Envoi Kafka -----
        producer.send(new ProducerRecord[String, String]("scoreboard", s.id.toString, write(s)))
        println(s"[Producer:Scoreboard] Sent record id=${s.id}, name=${s.name}")
        count += 1
      }

      if (count == 0) {
        println("[Producer:Scoreboard] No data found in `scoreboardplayers`. Waiting gracefully (no crash).")
        Thread.sleep(30000)
      } else {
        println(s"[Producer:Scoreboard] Finished → $count rows sent to Kafka topic `scoreboard`")
      }

      producer.close()
      conn.close()
    }
  }
