package consumer

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object SparkToAzureSql {

  // Azure SQL JDBC configuration
  val jdbcUrl =
    "jdbc:sqlserver://lol-sql-server.database.windows.net:1433;" +
      "database=lol-database;" +
      "user=dbadmin@lol-sql-server;" +
      "password=ESGI2025Spark!;" +
      "encrypt=true;trustServerCertificate=false;" +
      "hostNameInCertificate=*.database.windows.net;loginTimeout=30;"

  // Write batch function
  def writeBatch(df: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row], tableName: String): Unit = {
    df.write
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", tableName)
      .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
      .mode("append")
      .save()
  }

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("SparkToAzureSQL")
      .master("spark://spark-master:7077")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    import spark.implicits._

    // =============== PLAYERS STREAM ===============
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
      .option("kafka.bootstrap.servers", "kafka:9092")
      .option("subscribe", "players")
      .load()

    val playersParsed = playersStream
      .selectExpr("CAST(value AS STRING)")
      .select(from_json($"value", playersSchema).as("data"))
      .select("data.*")

    playersParsed.writeStream
      .foreachBatch { (batch: org.apache.spark.sql.DataFrame, batchId: Long) => writeBatch(batch, "dbo.Players")}
      .outputMode("append")
      .start()


    // =============== SCOREBOARD STREAM ===============
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
      .option("kafka.bootstrap.servers", "kafka:9092")
      .option("subscribe", "scoreboard")
      .load()

    val scoreboardParsed = scoreboardStream
      .selectExpr("CAST(value AS STRING)")
      .select(from_json($"value", scoreboardSchema).as("data"))
      .select("data.*")

    scoreboardParsed.writeStream
      .foreachBatch { (batch: org.apache.spark.sql.DataFrame, batchId: Long) => writeBatch(batch, "dbo.Scoreboard")}
      .outputMode("append")
      .start()
      .awaitTermination()
  }
}
