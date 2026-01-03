package consumer

import common.Config
import consumer.schemas.{PlayerSchema, ScoreboardSchema}
import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import java.sql.{Connection, DriverManager, Statement}

object Utils {

    def writeToAzure(tableName: String)(batchDF: DataFrame, batchId: Long): Unit = {
        writeToAzure(tableName, useUpsert = false)(batchDF, batchId)
    }

    def writeToAzure(tableName: String, useUpsert: Boolean = false)(batchDF: DataFrame, batchId: Long): Unit = {
        try {
        if (batchDF == null || batchDF.isEmpty) {
            println(s"[Azure] Batch $batchId vide → ignoré")
            return
        }

        val count = batchDF.count()
        println(s"[Azure] Batch $batchId : écriture de $count lignes → $tableName")

        if (useUpsert && tableName == "dbo.PlayerStats") {
            // Utiliser MERGE pour upsert (UPDATE ou INSERT)
            writeWithUpsert(batchDF, tableName, batchId)
        } else {
            // Mode append classique
            batchDF.write
                .format("jdbc")
                .option("url", Config.azureJdbcUrl)
                .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .option("dbtable", tableName)
                .option("user", Config.azureUser)
                .option("password", Config.azurePassword)
                .mode("append")
                .save()
        }

        println(s"[Azure] ✅ Batch $batchId : $count lignes écrites avec succès")
        } catch {
        case e: Exception =>
            println(s"[Azure] ❌ Batch $batchId : erreur → ${e.getMessage}")
            e.printStackTrace()
        }
    }

    private def writeWithUpsert(batchDF: DataFrame, tableName: String, batchId: Long): Unit = {
        val tempTable = s"#temp_${batchId}_${System.currentTimeMillis()}"
        
        // 1. Écrire dans une table temporaire
        batchDF.write
            .format("jdbc")
            .option("url", Config.azureJdbcUrl)
            .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
            .option("dbtable", tempTable)
            .option("user", Config.azureUser)
            .option("password", Config.azurePassword)
            .mode("overwrite")
            .save()

        // 2. Exécuter MERGE SQL
        var conn: Connection = null
        var stmt: Statement = null
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
            conn = DriverManager.getConnection(Config.azureJdbcUrl, Config.azureUser, Config.azurePassword)
            stmt = conn.createStatement()

            val mergeSQL = s"""
                MERGE INTO $tableName AS target
                USING $tempTable AS source
                ON target.playerName = source.playerName
                WHEN MATCHED THEN
                    UPDATE SET
                        avg_kills = source.avg_kills,
                        avg_deaths = source.avg_deaths,
                        avg_assists = source.avg_assists,
                        sum_kills = source.sum_kills,
                        sum_deaths = source.sum_deaths,
                        sum_assists = source.sum_assists,
                        avg_kda = source.avg_kda,
                        games_played = source.games_played,
                        kda_from_sums = source.kda_from_sums,
                        updated_at = source.updated_at
                WHEN NOT MATCHED THEN
                    INSERT (playerName, avg_kills, avg_deaths, avg_assists, sum_kills, sum_deaths, sum_assists, avg_kda, games_played, kda_from_sums, updated_at)
                    VALUES (source.playerName, source.avg_kills, source.avg_deaths, source.avg_assists, source.sum_kills, source.sum_deaths, source.sum_assists, source.avg_kda, source.games_played, source.kda_from_sums, source.updated_at);
            """

            val rowsAffected = stmt.executeUpdate(mergeSQL)
            println(s"[Azure] 🔄 MERGE exécuté : $rowsAffected lignes affectées")

            // Nettoyer la table temporaire
            stmt.executeUpdate(s"DROP TABLE IF EXISTS $tempTable")
        } finally {
            if (stmt != null) stmt.close()
            if (conn != null) conn.close()
        }
    }
    }