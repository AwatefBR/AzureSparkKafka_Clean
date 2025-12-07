package consumer.schemas

import org.apache.spark.sql.types._

object PlayerSchema {
  
  // Schéma Spark pour le topic players (avec rowId ajouté par le producer)
  val schema: StructType = StructType(Seq(
    StructField("id", StringType, nullable = true),
    StructField("overviewpage", StringType, nullable = true),
    StructField("player", StringType, nullable = true),
    StructField("image", StringType, nullable = true),
    StructField("name", StringType, nullable = true),
    StructField("nativename", StringType, nullable = true),
    StructField("namealphabet", StringType, nullable = true),
    StructField("namefull", StringType, nullable = true),
    StructField("country", StringType, nullable = true),
    StructField("nationality", StringType, nullable = true),
    StructField("nationalityprimary", StringType, nullable = true),
    StructField("age", StringType, nullable = true),
    StructField("residencyformer", StringType, nullable = true),
    StructField("team", StringType, nullable = true),
    StructField("team2", StringType, nullable = true),
    StructField("currentteams", StringType, nullable = true),
    StructField("teamsystem", StringType, nullable = true),
    StructField("team2system", StringType, nullable = true),
    StructField("residency", StringType, nullable = true),
    StructField("role", StringType, nullable = true),
    StructField("favchamps", StringType, nullable = true),
    StructField("birthdate", StringType, nullable = true),
    StructField("deathdate", StringType, nullable = true),
    StructField("rowId", LongType, nullable = true) // Ajouté par le producer
  ))
}

