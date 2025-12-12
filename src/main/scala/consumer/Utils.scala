package consumer

import common.Config
import consumer.schemas.{PlayerSchema, ScoreboardSchema}
import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger

object Utils {

    def writeToAzure(tableName: String)(batchDF: DataFrame, batchId: Long): Unit = {
        try {
        if (batchDF == null || batchDF.isEmpty) {
            println(s"[Azure] Batch $batchId vide → ignoré")
            return
        }

        val count = batchDF.count()
        println(s"[Azure] Batch $batchId : écriture de $count lignes → $tableName")

        batchDF.write
            .format("jdbc")
            .option("url", Config.azureJdbcUrl)
            .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
            .option("dbtable", tableName)
            .option("user", Config.azureUser)
            .option("password", Config.azurePassword)
            .mode("append")
            .save()

        println(s"[Azure] ✅ Batch $batchId : $count lignes écrites avec succès")
        } catch {
        case e: Exception =>
            println(s"[Azure] ❌ Batch $batchId : erreur → ${e.getMessage}")
            e.printStackTrace()
        }
    }
    }