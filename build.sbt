name := "lol-streaming"
version := "0.1.0"
scalaVersion := "2.12.19"

val sparkVersion = "3.5.1"

libraryDependencies ++= Seq(
  // --- Spark Core + SQL ---
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",

  // --- Kafka ---
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "org.apache.kafka" % "kafka-clients" % "3.5.1",

  // --- JDBC Drivers ---
  "org.postgresql" % "postgresql" % "42.7.3",              // pour PostgreSQL
  "com.microsoft.sqlserver" % "mssql-jdbc" % "12.6.1.jre11", // pour Azure SQL

  // --- JSON + HTTP Utils ---
  "com.lihaoyi" %% "upickle" % "3.1.2",
  "com.lihaoyi" %% "requests" % "0.8.0",

  // --- Logging ---
  "ch.qos.logback" % "logback-classic" % "1.5.6"
)

// Permet d'avoir plusieurs classes main dans le même jar
assembly / mainClass := Some("producer.MainApp");

// Gestion des conflits de META-INF
assemblyMergeStrategy := {
  case PathList("META-INF", _ @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

// Options Scala (pour compilation plus propre)
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-encoding", "utf-8"
)
