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