# Bonnes Pratiques : MERGE avec Spark Streaming et Azure SQL

## 🔍 Pourquoi les autres consumers fonctionnent

### `SparkToAzureSql` (Players et Scoreboard)

**Approche utilisée** :
```scala
batchDF.write
  .format("jdbc")
  .mode("append")  // ← Simple INSERT
  .save()
```

**Pourquoi ça fonctionne** :
1. **Mode append simple** : Insère de nouvelles lignes sans vérifier les doublons
2. **Déduplication avant écriture** : Utilise `dropDuplicates()` sur `uniqueline` ou `overviewpage` AVANT l'écriture
3. **Pas de contrainte PRIMARY KEY** : Les tables `dbo.Players` et `dbo.Scoreboard` acceptent potentiellement des doublons, ou la déduplication est faite en amont
4. **Pas besoin de MERGE** : Chaque ligne est traitée comme nouvelle

### `PlayersStats` - Pourquoi MERGE est nécessaire

**Problème** :
- Agrégation par `playerName` → un seul enregistrement par joueur
- Table `dbo.PlayerStats` avec `playerName` comme PRIMARY KEY
- Mode `outputMode("update")` → doit UPDATE si existe, INSERT sinon
- **Besoin de MERGE** pour éviter les erreurs de clé primaire

## ❌ Problème avec l'approche actuelle

### Tables temporaires locales (`#temp`)

```scala
val tempTable = s"#temp_${batchId}_${System.currentTimeMillis()}"
```

**Pourquoi ça ne fonctionne pas** :
1. **Tables temporaires locales** (`#temp`) sont **visibles uniquement dans la session JDBC qui les crée**
2. Spark JDBC écrit dans une session → table créée dans session A
3. Connection JDBC pour MERGE = nouvelle session B → **ne voit pas la table**
4. Erreur : `Invalid object name '#temp_...'`

## ✅ Bonnes Pratiques pour MERGE avec Spark + Azure SQL

### Option 1 : Table temporaire globale (Recommandé)

```scala
val tempTable = s"##temp_${batchId}_${System.currentTimeMillis()}"
```

**Avantages** :
- Tables `##temp` (double `#`) sont **globales** et visibles par toutes les sessions
- Simple à implémenter
- Nettoyage automatique à la fermeture de la connexion

**Inconvénients** :
- Peut causer des conflits si plusieurs batches s'exécutent en parallèle (rare avec streaming)

### Option 2 : Table permanente temporaire (Plus sûr)

```scala
val tempTable = s"temp_merge_${batchId}_${System.currentTimeMillis()}"
// Table normale (pas temporaire)
// Nettoyage explicite après MERGE
```

**Avantages** :
- Pas de problème de visibilité entre sessions
- Contrôle total sur le cycle de vie
- Pas de conflits entre batches

**Inconvénients** :
- Nécessite un nettoyage explicite
- Peut laisser des tables orphelines en cas d'erreur

### Option 3 : MERGE direct avec batch (Plus complexe)

Éviter la table temporaire en faisant le MERGE directement ligne par ligne :

```scala
// Pour chaque ligne du DataFrame
batchDF.foreachPartition { partition =>
  val conn = DriverManager.getConnection(...)
  partition.foreach { row =>
    // MERGE pour chaque ligne
    val mergeSQL = s"""
      MERGE INTO $tableName AS target
      USING (VALUES (...)) AS source(...)
      ON target.playerName = source.playerName
      ...
    """
    conn.createStatement().executeUpdate(mergeSQL)
  }
}
```

**Avantages** :
- Pas de table temporaire
- Plus de contrôle

**Inconvénients** :
- Plus lent (un MERGE par ligne)
- Plus complexe
- Risque de timeout sur grandes tables

### Option 4 : Utiliser une table staging permanente

Créer une table staging permanente, y écrire, faire le MERGE, puis truncate :

```scala
val stagingTable = "dbo.PlayerStats_Staging"
// Écrire dans staging
// MERGE FROM staging TO target
// TRUNCATE staging
```

**Avantages** :
- Pas de problème de visibilité
- Réutilisable
- Performant

**Inconvénients** :
- Nécessite une table supplémentaire
- Gestion de la table staging

## 🎯 Solution Recommandée : Table Temporaire Globale

Pour votre cas, la **Option 1** (table temporaire globale `##temp`) est la meilleure :

```scala
private def writeWithUpsert(batchDF: DataFrame, tableName: String, batchId: Long): Unit = {
    // Utiliser ## (double #) pour table temporaire GLOBALE
    val tempTable = s"##temp_${batchId}_${System.currentTimeMillis()}"
    
    // 1. Écrire dans table temporaire globale
    batchDF.write
        .format("jdbc")
        .option("url", Config.azureJdbcUrl)
        .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
        .option("dbtable", tempTable)
        .option("user", Config.azureUser)
        .option("password", Config.azurePassword)
        .mode("overwrite")
        .save()

    // 2. MERGE depuis la table temporaire globale
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

        // Nettoyer (optionnel, se fait automatiquement à la fermeture de connexion)
        stmt.executeUpdate(s"DROP TABLE IF EXISTS $tempTable")
    } finally {
        if (stmt != null) stmt.close()
        if (conn != null) conn.close()
    }
}
```

## 📊 Comparaison des approches

| Approche | Complexité | Performance | Fiabilité | Recommandé |
|----------|------------|-------------|-----------|------------|
| `##temp` (globale) | ⭐ Simple | ⭐⭐⭐ Excellente | ⭐⭐⭐ Très fiable | ✅ Oui |
| Table permanente | ⭐⭐ Moyenne | ⭐⭐⭐ Excellente | ⭐⭐ Bonne | ⚠️ Si besoin |
| MERGE ligne par ligne | ⭐⭐⭐ Complexe | ⭐ Lente | ⭐⭐ Bonne | ❌ Non |
| Table staging | ⭐⭐ Moyenne | ⭐⭐⭐ Excellente | ⭐⭐⭐ Très fiable | ✅ Si réutilisable |

## 🔑 Points Clés

1. **Tables `#temp` (locale)** = visible uniquement dans la session qui les crée
2. **Tables `##temp` (globale)** = visible par toutes les sessions
3. **Spark JDBC** crée une session différente de votre Connection JDBC
4. **Solution** : Utiliser `##temp` pour que les deux sessions voient la table



