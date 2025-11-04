name := "lol-streaming"
version := "0.1.0"
scalaVersion := "2.12.19"

libraryDependencies ++= Seq(
  // Spark provided by cluster
  "org.apache.spark" %% "spark-core" % "3.5.1" % "provided",
  "org.apache.spark" %% "spark-sql" % "3.5.1" % "provided",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.5.1",

  // Kafka client for producers
  "org.apache.kafka" % "kafka-clients" % "3.5.1",

  // JSON Serialize
  "com.lihaoyi" %% "upickle" % "3.1.2",

  // HTTP requests to Leaguepedia
  "com.lihaoyi" %% "requests" % "0.8.0",

  // Azure SQL JDBC driver
  "com.microsoft.sqlserver" % "mssql-jdbc" % "12.6.1.jre11"
)

assembly / mainClass := Some("consumer.SparkToAzureSql")

assemblyMergeStrategy := {
  case PathList("META-INF", _ @_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
