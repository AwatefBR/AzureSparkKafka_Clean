package consumer.schemas

import org.apache.spark.sql.types._

object TeamStatsSchema {
  val schema: StructType = StructType(Seq(
    StructField("team", StringType, nullable = false),
    StructField("kills", LongType, nullable = true),
    StructField("deaths", LongType, nullable = true),
    StructField("assists", LongType, nullable = true),
    StructField("kda", DoubleType, nullable = true),
    StructField("wins", LongType, nullable = true),
    StructField("losses", LongType, nullable = true),
    StructField("gamesPlayed", LongType, nullable = true),
    StructField("last_batch_id", LongType, nullable = true),
    StructField("updated_at", TimestampType, nullable = true)
  ))
}