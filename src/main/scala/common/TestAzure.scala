package common

import java.sql.{Connection, DriverManager, Statement}

object TestAzure {
  def main(args: Array[String]): Unit = {
    try {
      val server = sys.env("AZURE_SQL_SERVER")
      val db = sys.env("AZURE_SQL_DB")
      val user = sys.env("AZURE_SQL_USER")
      val password = sys.env("AZURE_SQL_PASSWORD")

      println(s"[Test Azure] Server: $server")
      println(s"[Test Azure] Database: $db")
      println(s"[Test Azure] User: $user")
      println(s"[Test Azure] Tentative de connexion...")

      val jdbcUrl = s"jdbc:sqlserver://$server:1433;" +
        s"database=$db;" +
        s"user=$user;" +
        s"password=$password;" +
        s"encrypt=true;" +
        s"trustServerCertificate=false;" +
        s"hostNameInCertificate=*.database.windows.net;" +
        s"loginTimeout=30;"

      Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
      val conn: Connection = DriverManager.getConnection(jdbcUrl)
      
      println(s"[Test Azure] ✅ Connexion réussie !")
      
      val stmt: Statement = conn.createStatement()
      val rs = stmt.executeQuery("SELECT @@VERSION AS Version, DB_NAME() AS CurrentDB")
      
      if (rs.next()) {
        println(s"[Test Azure] ✅ Version SQL Server: ${rs.getString("Version").take(50)}...")
        println(s"[Test Azure] ✅ Base de données actuelle: ${rs.getString("CurrentDB")}")
      }
      
      rs.close()
      stmt.close()
      conn.close()
      println(s"[Test Azure] ✅ Test réussi - Connexion Azure SQL fonctionne parfaitement !")
      
    } catch {
      case e: Exception =>
        println(s"[Test Azure] ❌ Erreur: ${e.getClass.getSimpleName}")
        println(s"[Test Azure] ❌ Message: ${e.getMessage}")
        if (e.getCause != null) {
          println(s"[Test Azure] ❌ Cause: ${e.getCause.getMessage}")
        }
        e.printStackTrace()
        System.exit(1)
    }
  }
}


