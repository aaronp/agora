package lupin.view

import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import lupin.BaseFlowSpec
import lupin.mongo.ParsedMongo
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.mongodb.scala.{ChangeStreamObservable, Document, MongoDatabase}
import org.mongodb.scala.model.CreateCollectionOptions
import org.reactivestreams.Publisher
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

  "Crud" should {
    "be able to create, update and delete" in {

      val opts = CreateCollectionOptions()
      opts.getIndexOptionDefaults
      val coll = db.getOrCreateCollection("basic", testDB, opts)

      import lupin.mongo.implicits._
      val b = Map("name" -> -1).asBson
      coll.createIndex(b).foreach(r => logger.info(s"Created $r"))

      val w: ChangeStreamObservable[Document] = coll.watch()


      val crud: Crud[String, Json] = Crud[Json](coll)
      val id = UUID.randomUUID().toString

      val dave: Publisher[crud.CreateResultType] = crud.create(id, Json.fromString("Dave"))

      val completedResults = Observable.fromReactivePublisher(dave).toListL.runAsync.futureValue
      completedResults.size shouldBe 1
    }
  }

}
