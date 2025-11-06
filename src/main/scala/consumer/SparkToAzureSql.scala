package consumer

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object SparkToAzureSql {

  // ---- JDBC URL ----
  private def jdbcUrl: String = {
    val server   = sys.env.getOrElse("AZURE_SQL_SERVER", "lol-sql-server.database.windows.net")
    val db       = sys.env.getOrElse("AZURE_SQL_DB", "lol-database")
    val user     = sys.env.getOrElse("AZURE_SQL_USER", "dbadmin@lol-sql-server")
    val password = sys.env.getOrElse("AZURE_SQL_PASSWORD", "ESGI2025Spark!")
    s"jdbc:sqlserver://$server:1433;database=$db;user=$user;password=$password;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;"
  }

  // ---- Fonction générique d'écriture ----
  def writeToSql(tableName: String)(batch: DataFrame, batchId: Long): Unit = {
    batch.write
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", tableName)
      .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
      .mode("append")
      .save()
  }

  def main(args: Array[String]): Unit = {

    val masterUrl = sys.env.getOrElse("SPARK_MASTER", "spark://spark-master:7077")
    val bootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP", "kafka:9092")

    val spark = SparkSession.builder()
      .appName("SparkToAzureSQL")
      .master(masterUrl)
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
    import spark.implicits._

    // ---- SCHEMA PLAYERS ----
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
      .load()

    val playersParsed = playersStream
      .selectExpr("CAST(value AS STRING)")
      .select(from_json($"value", playersSchema).as("data"))
      .select("data.*")

    playersParsed.writeStream
      .foreachBatch(writeToSql("dbo.Players") _)
      .outputMode("append")
      .option("checkpointLocation", "/tmp/chk/players")
      .start()

    // ---- SCHEMA SCOREBOARD ----
    val scoreboardSchema = StructType(Seq(
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
      .load()

    val scoreboardParsed = scoreboardStream
      .selectExpr("CAST(value AS STRING)")
      .select(from_json($"value", scoreboardSchema).as("data"))
      .select("data.*")

    scoreboardParsed.writeStream
      .foreachBatch(writeToSql("dbo.Scoreboard") _)
      .outputMode("append")
      .option("checkpointLocation", "/tmp/chk/scoreboard")
      .start()
      .awaitTermination()
  }
}
