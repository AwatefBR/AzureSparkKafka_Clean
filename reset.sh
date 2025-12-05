#!/bin/bash

echo "======================================="
echo "   🔥 RESET COMPLET ENV KAFKA/SPARK 🔥"
echo "======================================="

### 0. Rebuild du JAR
echo "🔨 Compilation SBT..."
sbt clean assembly

### 1. Arrêt et nettoyage des containers de TON projet
echo "🛑 Arrêt des containers..."
docker compose down --remove-orphans

### 2. Suppression SEULEMENT des volumes liés à TON docker-compose
echo "🧹 Suppression des volumes du projet..."

project_prefix=$(basename "$PWD" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/_/g')
volumes=$(docker volume ls -q | grep "${project_prefix}")

if [ -n "$volumes" ]; then
  echo "$volumes" | xargs -r docker volume rm -f
  echo "✅ Volumes supprimés"
else
  echo "⚠️ Aucun volume trouvé pour prefix : $project_prefix"
fi

### 3. Nettoyage manuel des checkpoints Spark
echo "🗑 Suppression des checkpoints Spark locaux..."
rm -rf ./spark-checkpoints-players ./spark-checkpoints-scoreboardplayers || true

### 4. Recréer dossier d'init Postgres + copier dump
echo "💾 Réinitialisation du dump PostgreSQL..."
rm -rf postgres-init
mkdir -p postgres-init
cp db/dump.sql postgres-init/00-init.sql

### 5. Supprimer UNIQUEMENT les images de ton projet
echo "♻️ Suppression des images Docker du projet..."
docker images --format "{{.Repository}} {{.ID}}" | grep "$(basename $PWD)" | awk '{print $2}' | xargs -r docker rmi -f

### 6. Reconstruction propre
echo "🔨 Reconstruction complète..."
docker compose build --no-cache

### 7. Redémarrage
echo "🚀 Démarrage de l'environnement..."
docker compose up -d

echo "======================================="
echo "   ✅ RESET TERMINE — ENV CLEAN ✔"
echo "======================================="

docker ps
