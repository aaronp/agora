package lupin.view

import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import lupin.BaseFlowSpec
import lupin.mongo.ParsedMongo
import org.mongodb.scala.model.changestream.ChangeStreamDocument
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import org.scalatest.BeforeAndAfterAll

class CrudTest extends BaseFlowSpec with BeforeAndAfterAll with StrictLogging {

  var db: ParsedMongo = null

  var testDB: MongoDatabase = null

  override def beforeAll(): Unit = {
    db = ParsedMongo.load()
    testDB = db.databaseForName("CrudTest")
  }

  override def afterAll() = {
    testDB.drop().toFuture().futureValue
    db.close()
    db = null
    testDB = null
  }

  def createIndex(coll: MongoCollection[Document]) = {
    import lupin.mongo.implicits._
    val b = Map("name" -> -1).asBson
    coll.createIndex(b).foreach(r => logger.info(s"Created $r"))
    coll
  }

  "Crud" should {
    "be able to create, update and delete" ignore {
      val coll = db.getOrCreateCollection("basic", testDB)
      createIndex(coll)

      val crud = Crud[Json](coll)
      val id = UUID.randomUUID().toString

      crud.create(id, Json.fromString("Dave")).toFuture().futureValue


      //      val completedResults = Observable.fromReactivePublisher(dave).toListL.runAsync.futureValue
      //      completedResults.size shouldBe 1
    }
  }

  "Crud.watch" should {
    "be watch updates" in {

      val coll = db.getOrCreateCollection("basic", testDB)

      val crud = Crud[Json](coll)
      val id = UUID.randomUUID().toString
      import io.circe.syntax._

      // ensure the collection exists before we try to watch it
      crud.create(UUID.randomUUID().toString, Map("first" -> "doc", "here" -> "we go").asJson).toFuture().futureValue

      println("Watching...")
      coll.watch().foreach { update: ChangeStreamDocument[Document] =>
        println("performed: " + update.getOperationType)
        println("on: " + update.getDocumentKey)
        println("desc: " + update.getUpdateDescription)
        println("doc: " + update.getFullDocument)
      }

      println("Creating...")
      val credRes = crud.create(id, Json.fromString("Hello")).toFuture().futureValue
      println(credRes)


      val allResults: Seq[Document] = coll.find().collect().toFuture().futureValue

      println(allResults.mkString(s"Found ${allResults.size} results:\n","\n\n","\n\n"))



      println("Updating...")
      val u = crud.update(id, Json.fromString("World")).toFuture().futureValue
      println("update returned " + u)
      println("Deleting...")
      crud.delete(id)
      println("Done...")

    }
  }

}
