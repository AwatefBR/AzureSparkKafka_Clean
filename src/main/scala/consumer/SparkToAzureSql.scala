package consumer

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object SparkToAzureSql {

  // ============================================================
  // ⚙️ Fonction MERGE générique avec gestion des NULL et batch vide
  // ============================================================
  def mergeToSql(tableName: String)(batchDF: DataFrame, batchId: Long): Unit = {
    if (batchDF == null || batchDF.isEmpty) {
      println(s"[Azure] ⏩ Batch vide ignoré pour $tableName (batch $batchId)")
      return
    }

    val keyCols = Seq("id")
    if (!batchDF.columns.contains("id")) {
      println(s"[Azure] ⚠️ Colonne 'id' absente du batch pour $tableName, batch ignoré.")
      return
    }

    val cleanDF = batchDF.dropDuplicates(keyCols)
    val cols = cleanDF.columns
    val nonKeys = cols.filterNot(keyCols.contains)

    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
    val conn = java.sql.DriverManager.getConnection(jdbcUrl)
    conn.setAutoCommit(false)

    try {
      // ✅ Création automatique de table si absente
      val stmt = conn.createStatement()
      val createTableSQL =
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
      stmt.execute(createTableSQL)
      stmt.close()

      // ✅ Requête MERGE
      val mergeSql =
        s"""
           |MERGE $tableName AS target
           |USING (SELECT ${cols.map(_ => "?").mkString(", ")}) AS source (${cols.mkString(",")})
           |ON target.id = source.id
           |WHEN MATCHED THEN UPDATE SET ${nonKeys.map(c => s"target.$c = source.$c").mkString(", ")}
           |WHEN NOT MATCHED THEN INSERT (${cols.mkString(",")})
           |VALUES (${cols.map(c => s"source.$c").mkString(",")});
           |""".stripMargin

      cleanDF.foreachPartition { partition: Iterator[org.apache.spark.sql.Row] =>
        if (partition.hasNext) {
          val ps = conn.prepareStatement(mergeSql)
          var count = 0
          partition.foreach { row =>
            var i = 1
            cols.foreach { c =>
              val v = row.getAs[Any](c)
              if (v == null) ps.setNull(i, java.sql.Types.NVARCHAR)
              else ps.setObject(i, v)
              i += 1
            }
            ps.addBatch()
            count += 1
            if (count % 500 == 0) ps.executeBatch()
          }
          ps.executeBatch()
          ps.close()
        }
      }

      conn.commit()
      println(s"[Azure] ✅ Batch $batchId fusionné dans $tableName (${cleanDF.count()} lignes)")

    } catch {
      case e: Exception =>
        println(s"[Azure] ❌ Erreur d’écriture dans $tableName (batch $batchId) : ${e.getMessage}")
        conn.rollback()
    } finally {
      conn.close()
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
  def main(args: Array[String]): Unit = {

    println("✅ SparkToAzureSql lancé !")

    val masterUrl = sys.env.getOrElse("SPARK_MASTER", "spark://spark-master:7077")
    val bootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP", "kafka:9092")

    val spark = SparkSession.builder()
      .appName("SparkToAzureSQL")
      .master(masterUrl)
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    // ============================================================
    // 🧍 STREAM 1 — PLAYERS
    // ============================================================
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

    val playersQuery = playersParsed.writeStream
      .foreachBatch(mergeToSql("dbo.Players") _)
      .outputMode("append")
      .option("checkpointLocation", "/tmp/checkpoints/players")
      .start()

    // ============================================================
    // 🎮 STREAM 2 — SCOREBOARD
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

    val scoreboardQuery = scoreboardParsed.writeStream
      .foreachBatch(mergeToSql("dbo.Scoreboard") _)
      .outputMode("append")
      .option("checkpointLocation", "/tmp/checkpoints/scoreboard")
      .start()

    // ============================================================
    // 🕓 Bloque le programme pour garder les streams actifs
    // ============================================================
    spark.streams.awaitAnyTermination()
  }
}
