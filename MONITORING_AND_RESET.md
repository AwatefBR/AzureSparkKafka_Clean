# Monitoring et Reset du Consumer PlayersStats

## 📊 Monitoring Ajouté

Le consumer `PlayersStats` affiche maintenant dans les logs :

```
[PlayersStats] 📊 Batch 29 →
  - Joueurs dans le batch: 15
  - Matchs dans le batch: 45
  - Joueurs uniques totaux: 308
  - Matchs traités totaux: 1250
  - Écriture vers Azure SQL (MERGE/UPSERT)...
[Azure] 🔄 MERGE exécuté : 15 lignes affectées
[PlayersStats] ✅ Batch 29 terminé
```

### Métriques disponibles :
- **Joueurs dans le batch** : Nombre de joueurs uniques dans le batch actuel
- **Matchs dans le batch** : Nombre total de matchs traités dans ce batch (somme des `games_played`)
- **Joueurs uniques totaux** : Nombre cumulé de joueurs uniques vus depuis le démarrage
- **Matchs traités totaux** : Nombre cumulé de matchs traités depuis le démarrage

## 🔧 Corrections Apportées

### 1. Problème de clé primaire dupliquée ✅
**Avant** : Utilisait `mode("append")` → erreur `PRIMARY KEY constraint violation`
**Maintenant** : Utilise `MERGE` SQL pour faire un UPSERT (UPDATE si existe, INSERT sinon)

### 2. Monitoring ✅
Ajout de logs détaillés pour suivre :
- Nombre de joueurs traités
- Nombre de matchs traités
- Statistiques par batch

## 🔄 Reset du Consumer (Repartir depuis le début)

### Question : Si on supprime la base, le consumer va-t-il recommencer depuis le début ?

**Réponse** : **NON**, car le checkpoint Kafka est stocké séparément.

### Pourquoi ?
Le checkpoint est stocké dans `/checkpoints/playerstats` (dans le volume Docker). Même si vous supprimez la table `dbo.PlayerStats` dans Azure SQL, le checkpoint Kafka reste et le consumer reprendra là où il s'était arrêté.

### Comment repartir depuis le début ?

#### Option 1 : Supprimer le checkpoint (recommandé)
```bash
# Sur la VM ou dans le conteneur
docker exec -it <container-consumer> rm -rf /checkpoints/playerstats

# Ou depuis la VM si le volume est monté
sudo rm -rf /var/lib/docker/volumes/<volume-name>/_data/playerstats
```

#### Option 2 : Supprimer le volume Docker
```bash
# Lister les volumes
docker volume ls | grep checkpoint

# Supprimer le volume
docker volume rm <volume-name>
```

#### Option 3 : Via docker-compose
```bash
# Arrêter le service
docker compose stop spark-consumer-players-stats

# Supprimer le volume
docker compose down -v

# Redémarrer (créera un nouveau volume vide)
docker compose up -d spark-consumer-players-stats
```

### Vérifier le checkpoint actuel
```bash
# Dans le conteneur
docker exec -it <container-consumer> ls -la /checkpoints/playerstats

# Voir les offsets Kafka stockés
docker exec -it <container-consumer> cat /checkpoints/playerstats/sources/0/*/metadata
```

## 📝 Workflow Complet de Reset

### 1. Arrêter le consumer
```bash
docker compose stop spark-consumer-players-stats
```

### 2. Supprimer la table Azure SQL (optionnel)
```sql
-- Dans Azure SQL
DROP TABLE dbo.PlayerStats;
```

### 3. Supprimer le checkpoint
```bash
# Option A : Supprimer le volume
docker compose down -v

# Option B : Supprimer manuellement
docker exec -it spark-consumer-players-stats rm -rf /checkpoints/playerstats
```

### 4. Redémarrer le consumer
```bash
docker compose up -d spark-consumer-players-stats
```

### 5. Vérifier les logs
```bash
docker compose logs -f spark-consumer-players-stats
```

Vous devriez voir :
```
[PlayersStats] 🚀 Démarrage du job PlayersStats (streaming)
[PlayersStats] 📊 Batch 0 →
  - Joueurs dans le batch: X
  - Matchs dans le batch: Y
  ...
```

## ⚠️ Important

- **Checkpoint = Position dans Kafka** : Le checkpoint stocke l'offset Kafka, pas l'état de la base de données
- **Supprimer la base ≠ Reset Kafka** : Pour repartir depuis le début, il faut supprimer le checkpoint
- **Perte de données** : Supprimer le checkpoint fait perdre la position, le consumer repartira depuis `startingOffsets` (actuellement `"latest"`)

## 🔍 Vérifier le nombre de joueurs dans Azure SQL

```sql
-- Compter les joueurs
SELECT COUNT(*) as total_players FROM dbo.PlayerStats;

-- Voir les statistiques
SELECT 
    COUNT(*) as total_players,
    SUM(games_played) as total_games,
    AVG(avg_kda) as avg_kda_overall
FROM dbo.PlayerStats;
```

## 📈 Monitoring en Temps Réel

Pour suivre les logs en temps réel :
```bash
docker compose logs -f spark-consumer-players-stats | grep -E "PlayersStats|Azure"
```

