# Guide : Comment checker les logs du Consumer PlayersStats

## 🚀 Commandes de base

### 1. Voir les logs en temps réel (suivi)
```bash
docker compose logs -f spark-consumer-players-stats
```

### 2. Voir les dernières lignes (100 dernières)
```bash
docker compose logs --tail=100 spark-consumer-players-stats
```

### 3. Voir les logs depuis un timestamp
```bash
docker compose logs --since 10m spark-consumer-players-stats  # 10 dernières minutes
docker compose logs --since 1h spark-consumer-players-stats    # 1 dernière heure
docker compose logs --since 2024-01-03T20:00:00 spark-consumer-players-stats
```

## 📊 Filtrer les logs importants

### Filtrer uniquement les logs PlayersStats et Azure
```bash
docker compose logs -f spark-consumer-players-stats | grep -E "PlayersStats|Azure"
```

### Voir uniquement les erreurs
```bash
docker compose logs spark-consumer-players-stats | grep -i "error\|exception\|failed\|❌"
```

### Voir uniquement les métriques de monitoring
```bash
docker compose logs spark-consumer-players-stats | grep -E "📊|Batch|Joueurs|Matchs"
```

### Voir les batches traités
```bash
docker compose logs spark-consumer-players-stats | grep -E "Batch [0-9]+"
```

## 🔍 Commandes avancées

### Combiner suivi + filtrage
```bash
docker compose logs -f spark-consumer-players-stats | grep --line-buffered -E "PlayersStats|Azure|📊|❌"
```

### Compter les batches traités
```bash
docker compose logs spark-consumer-players-stats | grep -c "Batch.*terminé"
```

### Voir les statistiques cumulatives
```bash
docker compose logs spark-consumer-players-stats | grep -E "Joueurs uniques totaux|Matchs traités totaux" | tail -1
```

### Voir les erreurs avec contexte (5 lignes avant/après)
```bash
docker compose logs spark-consumer-players-stats | grep -A 5 -B 5 -i "error\|exception"
```

## 📈 Monitoring en temps réel avec métriques

### Script de monitoring personnalisé
```bash
# Créer un script de monitoring
cat > monitor-players-stats.sh << 'EOF'
#!/bin/bash
echo "=== Monitoring PlayersStats Consumer ==="
echo ""
docker compose logs -f spark-consumer-players-stats | \
  grep --line-buffered -E "📊|Batch|Joueurs|Matchs|✅|❌|Azure" | \
  while IFS= read -r line; do
    echo "[$(date '+%H:%M:%S')] $line"
  done
EOF

chmod +x monitor-players-stats.sh
./monitor-players-stats.sh
```

## 🐛 Debugging

### Voir tous les logs (y compris Spark internes)
```bash
docker compose logs spark-consumer-players-stats
```

### Voir les logs Spark spécifiques
```bash
docker compose logs spark-consumer-players-stats | grep -i "spark\|dag\|executor"
```

### Voir les logs Kafka
```bash
docker compose logs spark-consumer-players-stats | grep -i "kafka\|offset\|topic"
```

### Voir les logs Azure SQL
```bash
docker compose logs spark-consumer-players-stats | grep -i "azure\|sql\|jdbc\|merge"
```

## 📝 Exemples de logs à surveiller

### ✅ Logs normaux (tout va bien)
```
[PlayersStats] 🚀 Démarrage du job PlayersStats (streaming)
[PlayersStats] 📊 Batch 29 →
  - Joueurs dans le batch: 15
  - Matchs dans le batch: 45
  - Joueurs uniques totaux: 308
  - Matchs traités totaux: 1250
  - Écriture vers Azure SQL (MERGE/UPSERT)...
[Azure] Batch 29 : écriture de 15 lignes → dbo.PlayerStats
[Azure] 🔄 MERGE exécuté : 15 lignes affectées
[Azure] ✅ Batch 29 : 15 lignes écrites avec succès
[PlayersStats] ✅ Batch 29 terminé
```

### ❌ Logs d'erreur (à surveiller)
```
[Azure] ❌ Batch 29 : erreur → ...
java.sql.BatchUpdateException: ...
[PlayersStats] Batch 29 vide
```

## 🎯 Commandes rapides (copier-coller)

### Suivi simple
```bash
docker compose logs -f spark-consumer-players-stats
```

### Suivi avec filtrage
```bash
docker compose logs -f spark-consumer-players-stats | grep --line-buffered -E "PlayersStats|Azure|📊|❌|✅"
```

### Dernières erreurs
```bash
docker compose logs spark-consumer-players-stats | grep -i "error\|exception" | tail -20
```

### Statistiques actuelles
```bash
docker compose logs spark-consumer-players-stats | grep -E "Joueurs uniques totaux|Matchs traités totaux" | tail -1
```

## 💡 Astuces

1. **Utiliser `--line-buffered` avec grep** : Pour voir les logs en temps réel avec filtrage
2. **Sauvegarder les logs** : `docker compose logs spark-consumer-players-stats > players-stats.log`
3. **Chercher un batch spécifique** : `docker compose logs spark-consumer-players-stats | grep "Batch 42"`
4. **Voir les logs depuis le démarrage** : `docker compose logs --since 0 spark-consumer-players-stats`

## 🔄 Alternative : Logs Docker directs

Si `docker compose` ne fonctionne pas, utilisez le nom du conteneur :

```bash
# Voir le nom exact du conteneur
docker ps | grep players-stats

# Utiliser le nom du conteneur
docker logs -f lol-streaming-clean-spark-consumer-players-stats-1

# Avec filtrage
docker logs -f lol-streaming-clean-spark-consumer-players-stats-1 | grep --line-buffered "PlayersStats"
```

