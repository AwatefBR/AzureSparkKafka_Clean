package common

import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import java.sql.{Connection, DriverManager}
import scala.io.Source

object Config {

  // Lecture obligatoire d'une variable d'env

  private def getEnv(name: String): String =
    sys.env.getOrElse(name,
      throw new RuntimeException(s"[Config] ❌ Missing required environment variable: $name")
    )

  // Kafka Configuration
  
  val bootstrap: String = getEnv("KAFKA_BOOTSTRAP")

  val kafkaProps: Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", bootstrap)
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props
  }

  val kafkaProducer: KafkaProducer[String, String] =
    new KafkaProducer[String, String](kafkaProps)

  // PostgreSQL Configuration
  // ============================================================
  val pgHost: String = getEnv("POSTGRES_HOST")
  val pgPort: String = sys.env.getOrElse("POSTGRES_PORT", "5432")
  val pgDb: String   = getEnv("POSTGRES_DB")
  val pgUser: String = getEnv("POSTGRES_USER")
  val pgPass: String = getEnv("POSTGRES_PASSWORD")

  val pgUrl: String = s"jdbc:postgresql://$pgHost:$pgPort/$pgDb"

  lazy val pgConnection: Connection = {
    Class.forName("org.postgresql.Driver")
    DriverManager.getConnection(pgUrl, pgUser, pgPass)
  }

  //  Azure SQL Configuration (lazy - only initialized when needed)
  // ============================================================
  lazy val azureServer: String   = getEnv("AZURE_SQL_SERVER")
  lazy val azureDb: String       = getEnv("AZURE_SQL_DB")
  lazy val azureUser: String     = getEnv("AZURE_SQL_USER")
  lazy val azurePassword: String = getEnv("AZURE_SQL_PASSWORD")

  lazy val azureJdbcUrl: String =
    s"jdbc:sqlserver://$azureServer:1433;" +
      s"database=$azureDb;" +
      s"user=$azureUser;" +
      s"password=$azurePassword;" +
      s"encrypt=true;" +
      s"trustServerCertificate=false;" +
      s"hostNameInCertificate=*.database.windows.net;" +
      s"loginTimeout=30;"

  lazy val azureConnection: Connection = {
    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
    DriverManager.getConnection(azureJdbcUrl)
  }

  
}
