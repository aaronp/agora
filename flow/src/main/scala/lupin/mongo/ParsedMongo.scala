package lupin.mongo

import com.typesafe.config.Config
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{Document, MongoClient, MongoCollection, ServerAddress}

case class ParsedMongo(client: MongoClient, config: Config) extends AutoCloseable {
  override def close(): Unit = client.close()

  lazy val db = client.getDatabase(config.getString("database"))

  lazy val defaultCollection: MongoCollection[Document] = db.getCollection(config.getString("defaultCollection"))
}

object ParsedMongo {

  def fromRootConfig(config: Config, pathToMongoConfig: String = "flow.mongo") = apply(config.getConfig(pathToMongoConfig))

  def apply(config: Config) = {


    import scala.collection.JavaConverters._
    val clusterSettings: ClusterSettings = ClusterSettings.builder().hosts(List(new ServerAddress("localhost")).asJava).build()
    //    val settings = MongoClientSettings.builder().

    val url = config.getString("url")
    val user = config.getString("user")
    val passwordChars = config.getString("password").toCharArray

    val client = MongoClient(url)


    val dbs = client.listDatabaseNames()

    //    val cxx: SingleObservable[Seq[String]] = dbs.collect()
    //    dbs.toFuture()

    dbs.foreach { db =>
      println(db)

    }

    new ParsedMongo(client, config.withoutPath("password"))
  }

}
