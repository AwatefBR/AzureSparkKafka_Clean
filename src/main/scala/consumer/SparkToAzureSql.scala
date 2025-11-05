package consumer

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object SparkToAzureSql {

  // JDBC URL construite depuis les variables d'environnement
  private def jdbcUrl: String = {
    val server   = sys.env.getOrElse("AZURE_SQL_SERVER", "lol-sql-server.database.windows.net")
    val db       = sys.env.getOrElse("AZURE_SQL_DB", "lol-database")
    val user     = sys.env.getOrElse("AZURE_SQL_USER", "dbadmin@lol-sql-server")
    val password = sys.env.getOrElse("AZURE_SQL_PASSWORD", "ESGI2025Spark!")
    s"jdbc:sqlserver://$server:1433;database=$db;user=$user;password=$password;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;"
  }

  private def writeBatch(df: org.apache.spark.sql.DataFrame, tableName: String): Unit = {
    df.write
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

    // PLAYERS SCHEMA
    val playersSchema = new StructType()
      .add("id", StringType)
      .add("overviewpage", StringType)
      .add("player", StringType)
      .add("image", StringType)
      .add("name", StringType)
      .add("nativename", StringType)
      .add("namealphabet", StringType)
      .add("namefull", StringType)
      .add("country", StringType)
      .add("nationality", StringType)
      .add("nationalityprimary", StringType)
      .add("age", StringType)
      .add("birthdate", StringType)
      .add("deathdate", StringType)
      .add("residencyformer", StringType)
      .add("team", StringType)
      .add("team2", StringType)
      .add("currentteams", StringType)
      .add("teamsystem", StringType)
      .add("team2system", StringType)
      .add("residency", StringType)
      .add("role", StringType)
      .add("favchamps", StringType)

    val playersStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrap)
      .option("subscribe", "players")
      .option("startingOffsets", "latest")
      .load()

    val playersParsed = playersStream
      .selectExpr("CAST(value AS STRING)")
      .select(from_json($"value", playersSchema).as("data"))
      .select("data.*")

    playersParsed.writeStream
      .foreachBatch(new org.apache.spark.api.java.function.VoidFunction2[org.apache.spark.sql.Dataset[org.apache.spark.sql.Row], java.lang.Long] {
        override def call(batch: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row], batchId: java.lang.Long): Unit = {
          writeBatch(batch, "dbo.Players")
        }
      })
      .outputMode("append")
      .option("checkpointLocation", "/tmp/chk/players")
      .start()


    // SCOREBOARD SCHEMA
    val scoreboardSchema = new StructType()
      .add("overviewpage", StringType)
      .add("name", StringType)
      .add("link", StringType)
      .add("champion", StringType)
      .add("kills", StringType)
      .add("deaths", StringType)
      .add("assists", StringType)
      .add("gold", StringType)
      .add("cs", StringType)
      .add("damagetochampions", StringType)
      .add("items", StringType)
      .add("teamkills", StringType)
      .add("teamgold", StringType)
      .add("team", StringType)
      .add("teamvs", StringType)
      .add("time", StringType)
      .add("playerwin", StringType)
      .add("datetime_utc", StringType)
      .add("dst", StringType)
      .add("tournament", StringType)
      .add("role", StringType)
      .add("uniqueline", StringType)
      .add("uniquelinevs", StringType)
      .add("gameid", StringType)
      .add("matchid", StringType)
      .add("gameteamid", StringType)
      .add("gameroleid", StringType)
      .add("gameroleidvs", StringType)
      .add("statspage", StringType)

    val scoreboardStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrap)
      .option("subscribe", "scoreboard")
      .option("startingOffsets", "latest")
      .load()

    val scoreboardParsed = scoreboardStream
      .selectExpr("CAST(value AS STRING)")
      .select(from_json($"value", scoreboardSchema).as("data"))
      .select("data.*")

    scoreboardParsed.writeStream
      .foreachBatch(new org.apache.spark.api.java.function.VoidFunction2[org.apache.spark.sql.Dataset[org.apache.spark.sql.Row], java.lang.Long] {
        override def call(batch: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row], batchId: java.lang.Long): Unit = {
          writeBatch(batch, "dbo.Scoreboard")
        }
      })
      .outputMode("append")
      .option("checkpointLocation", "/tmp/chk/scoreboard")
      .start()
      .awaitTermination()
  }
}
