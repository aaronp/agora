package lupin.view

import io.circe.{Encoder, Json}
import lupin.mongo.CirceToBson
import monix.execution.Scheduler
import org.mongodb.scala.bson.{BsonDocument, BsonString}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates
import org.mongodb.scala.result.{DeleteResult, UpdateResult}
import org.mongodb.scala.{Completed, Document, MongoCollection, SingleObservable}
trait Create[K, T] {
  type CreateResultType

  def create(key: K, value: T): CreateResultType
}

trait Update[K, T] {
  type UpdateResultType

  def update(key: K, value: T): UpdateResultType //ReactivePublisher[]
}

trait Delete[K] {
  type DeleteResultType

  def delete(key: K): DeleteResultType //ReactivePublisher[]
}

trait Crud[K, T] extends Create[K, T] with Update[K, T] with Delete[K]


object Crud {

  trait AsBson[T] {
    def asBson(value: T): BsonDocument

    def asBsonWithKey(value: T, key: String): BsonDocument
  }

  object AsBson {

    implicit object Identity extends AsBson[BsonDocument] {
      override def asBson(value: BsonDocument): BsonDocument = value

      override def asBsonWithKey(value: BsonDocument, key: String): BsonDocument = {
        value.append("_id", new BsonString(key))
      }
    }

    implicit object FromJson extends AsBson[Json] {
      override def asBson(value: Json): BsonDocument = {
        CirceToBson(value) match {
          case doc: BsonDocument => doc
          case bson =>

            BsonDocument(List("data" -> bson))
        }
      }

      override def asBsonWithKey(value: Json, key: String): BsonDocument = {
        Identity.asBsonWithKey(asBson(value), key)
      }
    }

    implicit def fromEncoded[T: Encoder]: AsBson[T] = new AsBson[T] {
      override def asBsonWithKey(value: T, key: String) = {
        Identity.asBsonWithKey(asBson(value), key)
      }

      override def asBson(value: T) = {
        import io.circe.syntax._
        val json = value.asJson
        CirceToBson.asBson(json)
      }
    }
  }

  def apply[K, T](implicit sched : Scheduler) = new InMemoryCrud[K, T]()

  def apply[T: AsBson](mongo: MongoCollection[Document]) = new MongoCrud[T](mongo)

  class InMemoryCrud[K, T](implicit sched : Scheduler) extends Crud[K, T] {
    override type CreateResultType = Option[T]
    override type UpdateResultType = Option[T]
    override type DeleteResultType = Boolean

    private var valuesById = Map[K, T]()

    override def create(key: K, value: T) = {
      val oldValue: Option[T] = valuesById.get(key)
      valuesById = valuesById.updated(key, value)
//      val res = Observable(oldValue).toReactivePublisher
      oldValue
    }

    override def update(key: K, value: T) = {
      create(key, value)
    }

    override def delete(key: K) = {
      val removed = valuesById.contains(key)
      valuesById = valuesById - key
      removed
    }
  }

  class MongoCrud[T: AsBson](mongo: MongoCollection[Document]) extends Crud[String, T] {
    val bsonConverter = implicitly[AsBson[T]]
    override type CreateResultType = SingleObservable[Completed]
    override type UpdateResultType = SingleObservable[UpdateResult]
    override type DeleteResultType = SingleObservable[DeleteResult]

      //adapters.observableAsPublisher(mongoRes)
    override def create(key: String, value: T): CreateResultType = {
      val bson = bsonConverter.asBsonWithKey(value, key)
      mongo.insertOne(bson)
    }
    override def update(key: String, value: T): UpdateResultType = {
      val newDoc = bsonConverter.asBsonWithKey(value, key)
      println(newDoc)
      mongo.updateOne(equal("_id", key), Updates.set("dave", newDoc))
    }
    override def delete(key: String): DeleteResultType = mongo.deleteOne(equal("_id", key))
  }

}