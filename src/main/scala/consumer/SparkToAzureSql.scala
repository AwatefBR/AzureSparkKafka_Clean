package consumer

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object SparkToAzureSql {

  // ============================================================
  // ⚙️ Fonction MERGE générique robuste (NULL, connexion partition, logs)
  // ============================================================
  def mergeToSql(tableName: String)(batchDF: DataFrame, batchId: Long): Unit = {
    // --- Vérifications préliminaires ---
    if (batchDF == null) {
      println(s"[Azure] ⏩ batch=$batchId : DF null → ignoré")
    } else if (batchDF.isEmpty) {
      println(s"[Azure] ⏩ batch=$batchId : DF vide → ignoré")
    } else if (!batchDF.columns.contains("id")) {
      println(s"[Azure] ⚠️ batch=$batchId : colonne 'id' absente → ignoré")
    } else {
      // --- Nettoyage Spark ---
      val cleaned =
        batchDF
          .withColumn("id", trim(col("id")))
          .filter(col("id").isNotNull && length(col("id")) > 0)
          .dropDuplicates("id")

      if (cleaned.isEmpty) {
        println(s"[Azure] ⏩ batch=$batchId : après filtre id non-null → 0 ligne")
      } else {
        // --- Log de détection JSON mal parsé ---
        println(s"[DEBUG] batch=$batchId — aperçu des 5 premières lignes du DataFrame avant écriture :")
        cleaned.show(5, truncate = false)

        val cols = cleaned.columns
        val keyCols = Seq("id")
        val nonKeys = cols.filterNot(keyCols.contains)

        // --- Création de la table si absente ---
        try {
          Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
          val createConn = java.sql.DriverManager.getConnection(jdbcUrl)
          try {
            val ddl =
              s"""
                 |IF OBJECT_ID('$tableName', 'U') IS NULL
                 |BEGIN
                 |  CREATE TABLE $tableName (
                 |    ${cols.map(c =>
                      if (c == "id") s"[$c] NVARCHAR(255) PRIMARY KEY"
                      else s"[$c] NVARCHAR(255)"
                    ).mkString(",\n    ")}
                 |  );
                 |END
                 |""".stripMargin
            val st = createConn.createStatement()
            st.execute(ddl)
            st.close()
          } finally {
            createConn.close()
          }
        } catch {
          case e: Exception =>
            println(s"[Azure] ⚠️ batch=$batchId : création table '$tableName' → ${e.getMessage}")
        }

        // --- Préparation requête MERGE ---
        val placeholders = cols.map(_ => "?").mkString(", ")
        val mergeSql =
          s"""
             |MERGE $tableName AS target
             |USING (SELECT $placeholders) AS source (${cols.mkString(",")})
             |ON target.id = source.id
             |WHEN MATCHED THEN UPDATE SET ${nonKeys.map(c => s"target.$c = source.$c").mkString(", ")}
             |WHEN NOT MATCHED THEN INSERT (${cols.mkString(",")})
             |VALUES (${cols.map(c => s"source.$c").mkString(",")});
             |""".stripMargin

        println(s"[Azure] ▶️ batch=$batchId : écriture dans $tableName | lignes=${cleaned.count()} | cols=${cols.mkString(",")}")

        // --- Écriture partition-par-partition ---
        cleaned.foreachPartition { it: Iterator[org.apache.spark.sql.Row] =>
          if (it.nonEmpty) {
            var conn: java.sql.Connection = null
            var ps: java.sql.PreparedStatement = null
            var written = 0

            try {
              Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
              conn = java.sql.DriverManager.getConnection(jdbcUrl)
              conn.setAutoCommit(false)
              ps = conn.prepareStatement(mergeSql)

              var cnt = 0
              it.foreach { row =>
                var i = 1
                cols.foreach { c =>
                  val v = row.getAs[Any](c)
                  if (v == null) ps.setNull(i, java.sql.Types.NVARCHAR)
                  else ps.setNString(i, v.toString)
                  i += 1
                }
                ps.addBatch()
                cnt += 1
                if (cnt % 500 == 0) {
                  ps.executeBatch()
                  conn.commit()
                  written += 500
                }
              }

              ps.executeBatch()
              conn.commit()
              written += (cnt % 500)

              println(s"[Azure] ✅ batch=$batchId : partition écrite → $written lignes")

            } catch {
              case e: Exception =>
                if (conn != null) try conn.rollback() catch { case _: Throwable => () }
                println(s"[Azure] ❌ batch=$batchId : erreur partition → ${e.getClass.getSimpleName}: ${e.getMessage}")
            } finally {
              if (ps != null) try ps.close() catch { case _: Throwable => () }
              if (conn != null) try conn.close() catch { case _: Throwable => () }
            }
          }
        }
      }
    }
  }

  // ============================================================
  // 🔗 Connexion JDBC
  // ============================================================
  private def jdbcUrl: String = {
    val server   = sys.env("AZURE_SQL_SERVER")
    val db       = sys.env("AZURE_SQL_DB")
    val user     = sys.env("AZURE_SQL_USER")
    val password = sys.env("AZURE_SQL_PASSWORD")

    s"jdbc:sqlserver://$server:1433;" +
      s"database=$db;" +
      s"user=$user;" +
      s"password=$password;" +
      s"encrypt=true;" +
      s"trustServerCertificate=false;" +
      s"hostNameInCertificate=*.database.windows.net;" +
      s"loginTimeout=30;"
  }

  // ============================================================
  // 🚀 Main : lancement des streams Kafka → Azure SQL
  // ============================================================
  def run(): Unit = {

    println("✅ SparkToAzureSql lancé !")

    val masterUrl = sys.env.getOrElse("SPARK_MASTER", "spark://spark-master:7077")
    val bootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP", "kafka:9092")

    val spark = SparkSession.builder()
      .appName("SparkToAzureSQL")
      .master(masterUrl)
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    // ============================================================
    // STREAM 1 — PLAYERS
    // ============================================================
    import org.apache.kafka.clients.admin.{AdminClient, ListTopicsOptions}
    import scala.collection.JavaConverters._

    val adminProps = new java.util.Properties()
    adminProps.put("bootstrap.servers", bootstrap)
    val adminClient = AdminClient.create(adminProps)

    val topicList = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000)).names().get().asScala
    val hasPlayersTopic = topicList.contains("players")
    adminClient.close()

    if (hasPlayersTopic) {
      println("Topic 'players' trouvé → démarrage du stream players")

      val playersSchema = StructType(Seq(
        StructField("id", StringType),
        StructField("overviewpage", StringType),
        StructField("player", StringType),
        StructField("image", StringType),
        StructField("name", StringType),
        StructField("nativename", StringType),
        StructField("namealphabet", StringType),
        StructField("namefull", StringType),
        StructField("country", StringType),
        StructField("nationality", StringType),
        StructField("nationalityprimary", StringType),
        StructField("age", StringType),
        StructField("birthdate", StringType),
        StructField("deathdate", StringType),
        StructField("residencyformer", StringType),
        StructField("team", StringType),
        StructField("team2", StringType),
        StructField("currentteams", StringType),
        StructField("teamsystem", StringType),
        StructField("team2system", StringType),
        StructField("residency", StringType),
        StructField("role", StringType),
        StructField("favchamps", StringType)
      ))

      val playersStream = spark.readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", bootstrap)
        .option("subscribe", "players")
        .option("startingOffsets", "earliest")
        .option("failOnDataLoss", "false")
        .load()

      val playersParsed = playersStream
        .selectExpr("CAST(value AS STRING)")
        .select(from_json(col("value"), playersSchema).as("data"))
        .select("data.*")
        .dropDuplicates("id")

      val playersCheckpointPath = s"/tmp/checkpoints/players_${System.currentTimeMillis()}"
      playersParsed.writeStream
        .foreachBatch(mergeToSql("dbo.Players") _)
        .outputMode("append")
        .option("checkpointLocation", playersCheckpointPath)
        .start()

      println(s"[Spark] ✅ Nouveau checkpoint créé : $playersCheckpointPath")

    } else {
      println("⚠️ Topic 'players' introuvable → stream ignoré, le consumer continue avec 'scoreboard'")
    }

    // ============================================================
    // STREAM 2 — SCOREBOARD
    // ============================================================
    val scoreboardSchema = StructType(Seq(
      StructField("id", StringType),
      StructField("overviewpage", StringType),
      StructField("name", StringType),
      StructField("link", StringType),
      StructField("champion", StringType),
      StructField("kills", StringType),
      StructField("deaths", StringType),
      StructField("assists", StringType),
      StructField("gold", StringType),
      StructField("cs", StringType),
      StructField("damagetochampions", StringType),
      StructField("items", StringType),
      StructField("teamkills", StringType),
      StructField("teamgold", StringType),
      StructField("team", StringType),
      StructField("teamvs", StringType),
      StructField("time", StringType),
      StructField("playerwin", StringType),
      StructField("datetime_utc", StringType),
      StructField("dst", StringType),
      StructField("tournament", StringType),
      StructField("role", StringType),
      StructField("gameid", StringType),
      StructField("matchid", StringType),
      StructField("gameteamid", StringType),
      StructField("gameroleid", StringType),
      StructField("gameroleidvs", StringType),
      StructField("statspage", StringType)
    ))

    val scoreboardStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrap)
      .option("subscribe", "scoreboard")
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load()

    val scoreboardParsed = scoreboardStream
      .selectExpr("CAST(value AS STRING)")
      .select(from_json(col("value"), scoreboardSchema).as("data"))
      .select("data.*")
      .dropDuplicates("id")

    val scoreboardCheckpointPath = s"/tmp/checkpoints/scoreboard_${System.currentTimeMillis()}"
    scoreboardParsed.writeStream
      .foreachBatch(mergeToSql("dbo.Scoreboard") _)
      .outputMode("append")
      .option("checkpointLocation", scoreboardCheckpointPath)
      .start()

    println(s"[Spark] ✅ Nouveau checkpoint créé : $scoreboardCheckpointPath")

    // ============================================================
    // 🕓 Boucle infinie
    // ============================================================
    spark.streams.awaitAnyTermination()
  }

  def main(args: Array[String]): Unit = {
    run()
  }
}
