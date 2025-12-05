package consumer.schemas

import org.apache.spark.sql.types._

object ScoreboardSchema {
  
  // Schéma Spark pour le topic scoreboardplayers (avec rowId ajouté par le producer)
  val schema: StructType = StructType(Seq(
    StructField("id", StringType, nullable = true),
    StructField("overviewpage", StringType, nullable = true),
    StructField("name", StringType, nullable = true),
    StructField("link", StringType, nullable = true),
    StructField("champion", StringType, nullable = true),
    StructField("kills", StringType, nullable = true),
    StructField("deaths", StringType, nullable = true),
    StructField("assists", StringType, nullable = true),
    StructField("gold", StringType, nullable = true),
    StructField("cs", StringType, nullable = true),
    StructField("damagetochampions", StringType, nullable = true),
    StructField("items", StringType, nullable = true),
    StructField("teamkills", StringType, nullable = true),
    StructField("teamgold", StringType, nullable = true),
    StructField("team", StringType, nullable = true),
    StructField("teamvs", StringType, nullable = true),
    StructField("time", StringType, nullable = true),
    StructField("playerwin", StringType, nullable = true),
    StructField("datetime_utc", StringType, nullable = true),
    StructField("dst", StringType, nullable = true),
    StructField("tournament", StringType, nullable = true),
    StructField("role", StringType, nullable = true),
    StructField("gameid", StringType, nullable = true),
    StructField("matchid", StringType, nullable = true),
    StructField("gameteamid", StringType, nullable = true),
    StructField("gameroleid", StringType, nullable = true),
    StructField("gameroleidvs", StringType, nullable = true),
    StructField("statspage", StringType, nullable = true),
    StructField("uniqueline", StringType, nullable = true),
    StructField("uniquelinevs", StringType, nullable = true),
    StructField("rowId", LongType, nullable = true) // Ajouté par le producer
  ))
}

