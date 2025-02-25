package server

import com.typesafe.config.{Config, ConfigFactory}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._

import java.net.{InetSocketAddress, Socket}
import java.sql.Connection

object DBManager {


  private val logger = LoggerFactory.getLogger("database")
  private val config: Config = ConfigFactory.load()

  lazy val PostgresSQL: JdbcBackend.Database = Database.forConfig("postgres")

  private val configPG = new HikariConfig()
  private lazy val dsPG = new HikariDataSource(configPG)

  try{
    configPG.setDriverClassName("org.postgresql.Driver")
    configPG.setJdbcUrl(s"jdbc:postgresql://${config.getString("postgres.properties.serverName")}:${config.getString("postgres.properties.portNumber")}/${config.getString("postgres.properties.databaseName")}")
    configPG.setUsername(config.getString("postgres.properties.user"))
    configPG.setPassword(config.getString("postgres.properties.password"))
    configPG.setMaximumPoolSize(config.getInt("postgres.numThreads"))
    dsPG
  }
  catch {
    case e: Throwable => logger.error(e.toString)
  }



  def start(): Boolean = {
    try {
      if (checkPostgres) {
        PostgresSQL
        true
      }
      else {
        false
      }
    }
    catch {
      case _: Throwable =>
        logger.error("Error starting Database")
        false
    }
  }


  private def checkPostgres: Boolean = {
    try {
      val sAddr = new InetSocketAddress(config.getString("postgres.properties.serverName"), config.getInt("postgres.properties.portNumber"))
      val socket = new Socket()
      socket.connect(sAddr, 1000)
      true
    }
    catch {
      case _: Throwable =>
        logger.error("Could not establish connection to PostgresSQL")
        false
    }
  }
  def GetPGConnection: Connection = dsPG.getConnection
}
