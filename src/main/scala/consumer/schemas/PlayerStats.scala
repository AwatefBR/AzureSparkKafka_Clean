package consumer.schemas

import org.apache.spark.sql.types._

object PlayerStatsSchema {
  val schema: StructType = StructType(Seq(
    StructField("playerName", StringType, false),
    StructField("avg_kills", DoubleType, true),
    StructField("avg_deaths", DoubleType, true),
    StructField("avg_assists", DoubleType, true),
    StructField("sum_kills", LongType, true),
    StructField("sum_deaths", LongType, true),
    StructField("sum_assists", LongType, true),
    StructField("avg_kda", DoubleType, true),
    StructField("kda_from_sums", DoubleType, true),
    StructField("updated_at", TimestampType, true)
  ))
}
