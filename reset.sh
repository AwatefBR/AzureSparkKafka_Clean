#!/bin/bash

# Ne pas utiliser set -e car certaines opérations peuvent échouer sans problème

echo "======================================="
echo "   🔥 RESET COMPLET ENV KAFKA + SPARK 🔥"
echo "======================================="

### 0. Arrêt complet (sans supprimer les volumes pour l'instant)
echo "🛑 Arrêt des containers..."
docker compose down --remove-orphans

### 1. Purge des topics Kafka AVANT de supprimer les volumes
echo "💣 Suppression des topics Kafka..."
docker compose up -d kafka zookeeper

sleep 5

# Utiliser kafka-topics (sans .sh) - commande Confluent Kafka 7.3.2
# Filtrer uniquement les lignes qui sont des noms de topics valides
topics=$(docker compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 --list 2>&1 | grep -E "^[a-zA-Z0-9_-]+$" | grep -v "^__" || echo "")

if [ -n "$topics" ]; then
  for t in $topics; do
    # Ignorer les topics système
    if [[ -n "$t" && ! "$t" =~ ^(__consumer_offsets|_schemas|__transaction_state) ]]; then
      echo "   → delete topic $t"
      docker compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 --delete --topic "$t" 2>/dev/null || true
    fi
  done
else
  echo "   → Aucun topic à supprimer"
fi

docker compose down

### 2. Purge complète des volumes liés au projet
echo "🧹 Suppression des volumes Docker..."
project_name=$(basename "$PWD" | tr '[:upper:]' '[:lower:]')

# Supprimer les volumes nommés explicitement
docker volume rm -f "${project_name}_spark-checkpoints-players" 2>/dev/null || true
docker volume rm -f "${project_name}_spark-checkpoints-players-stats" 2>/dev/null || true

docker volume rm -f "${project_name}_spark-checkpoints-scoreboardplayers" 2>/dev/null || true
docker volume rm -f "${project_name}_postgres-data" 2>/dev/null || true

# Supprimer tous les volumes du projet (fallback)
docker volume ls -q | grep "$project_name" | xargs -r docker volume rm -f || true

### 3. Purge des checkpoints Spark dans les volumes (si encore présents)
echo "🗑 Suppression checkpoints Spark dans volumes Docker..."
for vol in $(docker volume ls -q | grep "spark-checkpoints"); do
  echo "   → wipe volume $vol"
  docker run --rm -v $vol:/data busybox sh -c "rm -rf /data/*" 2>/dev/null || true
done

### 4. Suppression des dossiers checkpoints locaux (si existent)
echo "🗑 Suppression des checkpoints Spark locaux..."
rm -rf ./spark-checkpoints-* ./checkpoints 2>/dev/null || true

### 5. Purge des images de TON projet
echo "♻️ Suppression des images Docker du projet..."
docker images | grep "$project_name" | awk '{print $3}' | xargs -r docker rmi -f || true

### 6. Purge des réseaux Docker (si orphelins)
echo "🌐 Nettoyage des réseaux Docker..."
docker network prune -f

### 10. Attendre que PostgreSQL soit prêt et vérifier l'initialisation
echo "⏳ Attente de l'initialisation de PostgreSQL..."
sleep 10

# Vérifier que PostgreSQL a bien chargé le dump
if docker compose exec -T postgres psql -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-lol} -c "\dt" | grep -q "players\|scoreboardplayers"; then
  echo "✅ PostgreSQL initialisé avec succès"
else
  echo "⚠️  PostgreSQL peut nécessiter une réinitialisation manuelle"
fi

echo "======================================="
echo "   ✅ RESET TERMINÉ — ENV CLEAN ✔"
echo "======================================="
docker compose ps
