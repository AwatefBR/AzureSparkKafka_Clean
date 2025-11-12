README — Streaming temps réel League of Legends → Azure SQL
 Objectif du projet

Ce projet met en place un pipeline de données temps réel basé sur des données de matchs League of Legends.
Il collecte des informations provenant de l’API Riot (players & scoreboard), les envoie dans Kafka, puis les consomme avec Spark Structured Streaming afin de les stocker dans Azure SQL Database.

L’objectif est de :

- Traiter des flux en continu

- Nettoyer / transformer les données avant stockage

- Rendre les données disponibles pour l’analyse & la BI (Power BI)


Services deployés via Docker Compose :

| Service               | Rôle                                                 |
| --------------------- | ---------------------------------------------------- |
| `players-producer`    | Récupère les infos joueurs + stats                   |
| `scoreboard-producer` | Récupère les infos du scoreboard en match            |
| `kafka`               | Message broker pour le streaming                     |
| `zookeeper`           | Nécessaire au fonctionnement de Kafka                |
| `spark-master`        | Master du cluster Spark                              |
| `spark-worker`        | Worker exécutant les tâches Spark                    |
| `spark-consumer`      | Lit Kafka, transforme & écrit dans Azure SQL         |
| `kafka-ui`            | Interface graphique pour visualiser les topics Kafka |

|Pré-requis
| Logiciel       | Version    |
| -------------- | ---------- |
| Docker         | ≥ 24       |
| Docker Compose | ≥ 2        |
| Scala          | 2.12       |
| SBT            | ≥ 1.8      |
| Java           | OpenJDK 11 |

Azure SQL doit être accessible en public et la white-list IP configurée.

 Démarrer le pipeline

Compiler & packager le consumer Spark:

sbt clean assembly

La commande génère le jar :
target/scala-2.12/lol-streaming-assembly-0.1.0.jar

(Re)build des images Docker:

docker compose build

Lancer le cluster + producers + consumer:

docker compose up -d

Vérifier que tout fonctionne:

docker compose ps

Consulter les logs (exemples):

docker logs -f lol-streaming-clean-spark-consumer-1
docker logs -f lol-streaming-clean-players-producer-1
docker logs -f lol-streaming-clean-scoreboard-producer-1

 Arrêter l'ensemble
docker compose down

Durant le run:
Si l'espace disk est saturé
Check : df -h (si 100%) --> supprimer les conteneurs arrétés, images inutilisées, volumes non attachés --> docker system prune -a --volumes


Tout relancer (base propre) :
docker compose down -v --remove-orphans
sleep 5
docker system prune -af
sleep 5
docker volume prune -f
sleep 5
docker network prune -f
sleep 5
sbt clean assembly
sleep 5
docker compose build --no-cache
sleep 5
docker compose up -d
sleep 5


verifier que les topics existent :

docker exec -it lol-streaming-clean-kafka-1 kafka-topics \
  --bootstrap-server kafka:9092 --list

creer les topics manuellement:

docker exec -it lol-streaming-clean-kafka-1 kafka-topics \
  --create --topic players \
  --bootstrap-server kafka:9092 \
  --partitions 1 --replication-factor 1

docker exec -it lol-streaming-clean-kafka-1 kafka-topics \
  --create --topic scoreboard \
  --bootstrap-server kafka:9092 \
  --partitions 1 --replication-factor 1


Effacer les ckp defectueux

sudo rm -rf /tmp/checkpoints/scoreboard
sudo rm -rf /tmp/checkpoints/players

docker compose down
sudo rm -rf /tmp/checkpoints /tmp/spark*
docker system prune -a --volumes -f
sudo systemctl restart docker

tout supprimer y compris les volumes persistants
docker-compose down -v


SELECT *
FROM dbo.Scoreboard
ORDER BY CAST(id AS INT)
LIMIT 100 

java.io.IOException: No space left on device
🧩 Interprétation
Spark (ou ton container Docker) n’a plus d’espace disque disponible pour :

écrire ses fichiers temporaires (/tmp, /tmp/blockmgr-...)

stocker les checkpoints Spark

ou éventuellement gérer les shuffle / cache intermédiaires.

C’est une erreur du système de fichiers, pas une erreur applicative Spark.