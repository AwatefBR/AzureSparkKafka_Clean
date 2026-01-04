package consumer

import common.Config
import consumer.schemas.{PlayerSchema, ScoreboardSchema}
import org.apache.spark.sql.{SparkSession, DataFrame, Row}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import java.sql.{Connection, DriverManager, PreparedStatement}

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
        // Utiliser foreachPartition pour traiter les données par partition
        // Utiliser une table temporaire locale (#temp) dans la même connexion pour le MERGE
        // La table #temp est automatiquement supprimée quand la connexion se ferme
        
        // Capturer les valeurs de Config AVANT foreachPartition pour éviter les problèmes de sérialisation
        val jdbcUrl = Config.azureJdbcUrl
        val user = Config.azureUser
        val password = Config.azurePassword
        
        var totalRowsAffected = 0L

        batchDF.foreachPartition { partition: Iterator[Row] =>
            var conn: Connection = null
            var stmt: java.sql.Statement = null
            var insertStmt: PreparedStatement = null
            try {
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                conn = DriverManager.getConnection(jdbcUrl, user, password)
                stmt = conn.createStatement()

                // 1. Créer une table temporaire locale (#temp) dans cette session
                // Elle sera automatiquement supprimée à la fermeture de la connexion
                val createTempTableSQL = s"""
                    IF OBJECT_ID('tempdb..#temp_playerstats') IS NOT NULL DROP TABLE #temp_playerstats;
                    CREATE TABLE #temp_playerstats (
                        player_id NVARCHAR(255) NOT NULL,
                        avg_kills FLOAT,
                        avg_deaths FLOAT,
                        avg_assists FLOAT,
                        sum_kills FLOAT,
                        sum_deaths FLOAT,
                        sum_assists FLOAT,
                        avg_kda FLOAT,
                        games_played BIGINT,
                        kda_from_sums FLOAT,
                        updated_at DATETIME2
                    );
                """
                stmt.execute(createTempTableSQL)

                // 2. Insérer les données dans la table temporaire
                val insertSQL = s"""
                    INSERT INTO #temp_playerstats (player_id, avg_kills, avg_deaths, avg_assists, sum_kills, sum_deaths, sum_assists, avg_kda, games_played, kda_from_sums, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                insertStmt = conn.prepareStatement(insertSQL)

                var batchCount = 0
                partition.filter { row =>
                    // Filtrer les lignes avec player_id NULL ou vide
                    val playerId = if (row.isNullAt(0)) null else row.getAs[String]("player_id")
                    playerId != null && playerId.nonEmpty
                }.foreach { row =>
                    val playerId = row.getAs[String]("player_id")
                    insertStmt.setString(1, playerId)
                    insertStmt.setDouble(2, if (row.isNullAt(1)) null.asInstanceOf[Double] else row.getAs[Double]("avg_kills"))
                    insertStmt.setDouble(3, if (row.isNullAt(2)) null.asInstanceOf[Double] else row.getAs[Double]("avg_deaths"))
                    insertStmt.setDouble(4, if (row.isNullAt(3)) null.asInstanceOf[Double] else row.getAs[Double]("avg_assists"))
                    insertStmt.setDouble(5, if (row.isNullAt(4)) null.asInstanceOf[Double] else row.getAs[Double]("sum_kills"))
                    insertStmt.setDouble(6, if (row.isNullAt(5)) null.asInstanceOf[Double] else row.getAs[Double]("sum_deaths"))
                    insertStmt.setDouble(7, if (row.isNullAt(6)) null.asInstanceOf[Double] else row.getAs[Double]("sum_assists"))
                    insertStmt.setDouble(8, if (row.isNullAt(7)) null.asInstanceOf[Double] else row.getAs[Double]("avg_kda"))
                    insertStmt.setLong(9, if (row.isNullAt(8)) null.asInstanceOf[Long] else row.getAs[Long]("games_played"))
                    insertStmt.setDouble(10, if (row.isNullAt(9)) null.asInstanceOf[Double] else row.getAs[Double]("kda_from_sums"))
                    val updatedAt = if (row.isNullAt(10)) new java.sql.Timestamp(System.currentTimeMillis()) else row.getAs[java.sql.Timestamp]("updated_at")
                    insertStmt.setTimestamp(11, updatedAt)

                    insertStmt.addBatch()
                    batchCount += 1

                    // Exécuter par lots de 100
                    if (batchCount >= 100) {
                        insertStmt.executeBatch()
                        batchCount = 0
                    }
                }

                // Exécuter le reste
                if (batchCount > 0) {
                    insertStmt.executeBatch()
                }

                // 3. Exécuter MERGE depuis la table temporaire vers la table cible
                val mergeSQL = s"""
                    MERGE INTO $tableName AS target
                    USING #temp_playerstats AS source
                    ON target.player_id = source.player_id
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
                        INSERT (player_id, avg_kills, avg_deaths, avg_assists, sum_kills, sum_deaths, sum_assists, avg_kda, games_played, kda_from_sums, updated_at)
                        VALUES (source.player_id, source.avg_kills, source.avg_deaths, source.avg_assists, source.sum_kills, source.sum_deaths, source.sum_assists, source.avg_kda, source.games_played, source.kda_from_sums, source.updated_at);
                """

                val rowsAffected = stmt.executeUpdate(mergeSQL)
                totalRowsAffected += rowsAffected

            } catch {
                case e: Exception =>
                    println(s"[Azure] ❌ Erreur dans foreachPartition : ${e.getMessage}")
                    e.printStackTrace()
                    throw e
            } finally {
                if (insertStmt != null) insertStmt.close()
                if (stmt != null) stmt.close()
                if (conn != null) conn.close() // La table #temp est automatiquement supprimée
            }
        }

        println(s"[Azure] 🔄 MERGE exécuté : $totalRowsAffected lignes affectées")
    }
    }