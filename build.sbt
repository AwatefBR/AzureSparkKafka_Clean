name := "lol-streaming"
version := "0.1.0"
scalaVersion := "2.12.19"

val sparkVersion = "3.5.1"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",

  // INTÉGRÉS DANS LE JAR (pas provided)
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "org.apache.kafka" % "kafka-clients" % "3.5.1",

  // JSON + HTTP
  "com.lihaoyi" %% "upickle" % "3.1.2",
  "com.lihaoyi" %% "requests" % "0.8.0",

  // JDBC SQL Server
  "com.microsoft.sqlserver" % "mssql-jdbc" % "12.6.1.jre11"
)

assembly / mainClass := Some("consumer.SparkToAzureSql")

assemblyMergeStrategy := {
  case PathList("META-INF", _ @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
