README — lol-streaming-clean
======================================

Introduction
------------
Ce projet met en place un pipeline de streaming complet pour des données de matchs League of Legends exécuté depuis une VM Azure. Sur la VM, la base PostgreSQL est (re)construite à partir d'un dump SQL : les producers Spark lisent ces tables sources, publient des messages JSON dans Kafka, et plusieurs consumers Spark consomment ces topics pour recréer/mettre à jour quatre tables en sortie (ex.: `dbo.Players`, `dbo.Scoreboard`, `dbo.PlayerStats`, `dbo.TeamStats`), dont l'une est essentiellement un copier/coller et l'autre une transformation agrégée (TeamStats). Les résultats sont écrits dans Azure SQL pour être exploités en BI (Power BI).
    
But
----

Pipeline de streaming temps réel pour données League of Legends :

- Lire des tables sources dans PostgreSQL (dump fourni)
- Simuler / exporter ces données dans Kafka (producers Spark) 
- Consommer les topics Kafka avec Spark Structured Streaming (consumers)
- Transformer / agréger les données (PlayerStats, TeamStats...) et écrire dans **Azure SQL**
- Exploiter les données depuis Power BI (ou autre outil de BI)

Architecture (résumé)
--------------------

- Producers (services Docker) : lisent PostgreSQL et publient dans Kafka
- Kafka + Zookeeper : broker de messages
- PostgreSQL : base de données source (dump → restore)
- Spark (master + worker) : exécute producers et consumers
- Consumers Spark : lisent Kafka, appliquent schémas, dédup, calculs et écrivent dans Azure SQL
- Volumes Docker : stockent checkpoints Spark (résilience/reprise)

Principales briques (fichiers clés)
----------------------------------

- `docker-compose.yml` : composition des services (kafka, zookeeper, postgres, spark, producers/consumers)
- `Dockerfile.main` / `Dockerfile.consumer` : images pour producers et consumers
- `src/main/scala/producer/*` : producers (ex: `MainApp.scala`) — lisent Postgres, envoient `players` et `scoreboardplayers`
- `src/main/scala/consumer/*` : consumers (ex: `SparkToAzureSql.scala`, `PlayersStats.scala`)
- `src/main/scala/common/Config.scala` : variables d'environnement requises
- `db/dump.sql` et `postgres-init/` : scripts d'initialisation Postgres
- `reset.sh` : script utile pour nettoyer topics, volumes, images et relancer l'environnement

Flux de données (étapes)
------------------------

1. Rebuild PostgreSQL depuis le dump (sur votre VM Azure)
   - Option A (rapide, redéploiement propre) :
     ```bash
     docker compose down -v --remove-orphans
     docker compose up -d postgres
     # Le dossier `postgres-init/` est monté dans /docker-entrypoint-initdb.d et s'exécutera au premier démarrage
     ```
   - Option B (restauration manuelle d'un dump SQL existant) :
     ```bash
     # copier le dump dans la machine (si nécessaire) puis :
     docker compose up -d postgres
     sleep 5
     docker exec -i $(docker compose ps -q postgres) psql -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-lol} < db/dump.sql
     ```

2. Lancer / rebuild l'application
   ```bash
   sbt clean assembly                       # génère target/scala-2.12/lol-streaming-assembly-0.1.0.jar
   docker compose build --no-cache
   docker compose up -d
   ```

3. Vérifier les topics Kafka
   ```bash
   docker exec -it $(docker compose ps -q kafka) kafka-topics --bootstrap-server kafka:9092 --list
   ```
   - Topics utilisés par le code : **players** et **scoreboardplayers** (attention : l'ancien README mentionnait `scoreboard` mais le code produit `scoreboardplayers`).

4. Producers → Kafka
   - Les producers lisent les tables Postgres (`players`, `scoreboardplayers`), ajoutent une `rowId` et publient des messages JSON dans Kafka.

5. Consumers Spark → Azure SQL
   - `SparkToAzureSql` consomme les topics, parse le JSON avec un schéma strict, déduplique et écrit dans Azure SQL :
     - `dbo.Players` (copy des joueurs)
     - `dbo.Scoreboard` (copy des lignes scoreboard)
   - `PlayersStats` (job séparé) agrège les événements pour calculer des statistiques par joueur et écrit dans `dbo.PlayerStats`.
   - Schéma `TeamStatsSchema` est présent (dans `consumer/schemas`) — si un job TeamStats est ajouté, il pourra écrire `dbo.TeamStats` (aggrégations par équipe).

6. Visualisation
   - Connecter Power BI (ou autre outil) à **Azure SQL** pour créer dashboards / rapports.


Notes & points d'attention
--------------------------

- Azure SQL doit autoriser l'IP publique de la VM (white-list). Sinon, les writes échoueront.
- Checkpoints Spark : montés sur volumes Docker (`spark-checkpoints-*`). Pour repartir proprement, supprimer ou réinitialiser ces volumes (`docker volume rm -f ...`) ou utiliser `reset.sh`.
- Attention aux erreurs de disque plein : surveillez `df -h` et nettoyez images/volumes si nécessaire (`docker system prune -a --volumes`).
- `Dockerfile.consumer` télécharge des jars au build ; assurez-vous d'avoir connectivité réseau lors de `docker compose build`.
- Logs utiles :
  - `docker logs -f <container>`
  - `docker compose ps`

Troubleshooting rapide
---------------------

- Pas de messages Kafka → vérifier `KAFKA_BOOTSTRAP`, que les producers tournent et que les topics existent.
- Consumers ne se connectent pas à Azure SQL → vérifier variables `AZURE_SQL_*` et la whitelist IP sur le serveur SQL.
- Problèmes de reprise / doublons → vérifier les parcours de checkpoints et la configuration `checkpointLocation` dans les consumers.

Commandes utiles
-----------------

- Build jar : `sbt clean assembly`
- Build images : `docker compose build`
- Lancer tout : `docker compose up -d`
- Arrêter et supprimer volumes : `docker compose down -v --remove-orphans`
- Supprimer topics Kafka : utiliser `kafka-topics --delete` (script `reset.sh` automatise cela)



