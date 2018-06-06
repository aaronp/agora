package lupin.mongo

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.mongodb.scala._
import org.mongodb.scala.model.CreateCollectionOptions

import scala.reflect.ClassTag

case class ParsedMongo(client: MongoClient, config: Config) extends AutoCloseable {
  override def close(): Unit = client.close()

  lazy val db: MongoDatabase = databaseForName(config.getString("database"))

  def databaseForName(name : String) = client.getDatabase(name)

  def dropDatabase(name : String) = databaseForName(name).drop()

  def collectionFor[T: ClassTag](database: MongoDatabase = db, options : CreateCollectionOptions = CreateCollectionOptions()): MongoCollection[Document] = {
    val name = collectionName[T]
    getOrCreateCollection(name, database, options)
  }

  def collectionName[T: ClassTag]: String = {
    val safeName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.filter(_.isLetter)
    safeName
  }

  lazy val defaultCollection: MongoCollection[Document] = {
    val collectionName = config.getString("defaultCollection")
    getOrCreateCollection(collectionName)
  }

  def getOrCreateCollection(name: String, database: MongoDatabase = db, options : CreateCollectionOptions = CreateCollectionOptions()): MongoCollection[Document] = {
    database.createCollection(name, options)
    val coll = database.getCollection(name)
    coll
  }
}

object ParsedMongo extends StrictLogging {

  def load(config: Config = ConfigFactory.load(), pathToMongoConfig: String = "flow.mongo") = apply(config.getConfig(pathToMongoConfig))

  /**
    * See https://docs.mongodb.com/manual/reference/connection-string
    *
    * @param config
    * @return
    */
  def apply(config: Config): ParsedMongo = {
    val url = config.getString("url")
    val hostPort = url.split("@").lastOption
    logger.info(s"Connecting mongo client to ${hostPort.getOrElse("<invalid url>")}")
    val client = MongoClient(url)
    new ParsedMongo(client, config.withoutPath("password"))
  }

}
