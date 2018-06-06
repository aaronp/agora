package lupin.view

import io.circe.{Encoder, Json}
import lupin.mongo.{CirceToBson, adapters}
import monix.execution.Scheduler
import monix.reactive.Observable
import org.mongodb.scala.bson.{BsonDocument, BsonString}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.result.{DeleteResult, UpdateResult}
import org.mongodb.scala.{Completed, Document, MongoCollection, SingleObservable}
import org.reactivestreams.{Publisher => ReactivePublisher}

trait Create[K, T] {
  type CreateResultType

  def create(key: K, value: T): ReactivePublisher[CreateResultType]
}

trait Update[K, T] {
  type UpdateResultType

  def update(key: K, value: T): ReactivePublisher[UpdateResultType]
}

trait Delete[K] {
  type DeleteResultType

  def delete(key: K): ReactivePublisher[DeleteResultType]
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

    override def create(key: K, value: T): ReactivePublisher[CreateResultType] = {
      val oldValue: Option[T] = valuesById.get(key)
      valuesById = valuesById.updated(key, value)
      val res = Observable(oldValue).toReactivePublisher
      res
    }

    override def update(key: K, value: T): ReactivePublisher[UpdateResultType] = {
      create(key, value)
    }

    override def delete(key: K): ReactivePublisher[DeleteResultType] = {
      val removed = valuesById.contains(key)
      valuesById = valuesById - key
      val rp = Observable(removed).toReactivePublisher
      rp
    }
  }

  class MongoCrud[T: AsBson](mongo: MongoCollection[Document]) extends Crud[String, T] {
    val bsonConverter = implicitly[AsBson[T]]
    override type CreateResultType = Completed
    override type UpdateResultType = UpdateResult
    override type DeleteResultType = DeleteResult

    override def create(key: String, value: T): ReactivePublisher[CreateResultType] = {
      val doc: BsonDocument = bsonConverter.asBson(value)
      val mongoRes = mongo.insertOne(doc)
      val r: ReactivePublisher[Completed] = adapters.observableAsPublisher(mongoRes)
      r
    }


    override def update(key: String, value: T): ReactivePublisher[UpdateResultType] = {
      val doc = bsonConverter.asBson(value)
      val mongoRes = mongo.updateOne(equal("_id", key), doc)
      adapters.observableAsPublisher(mongoRes)
    }


    def doDelete(key: String): ReactivePublisher[DeleteResultType] = {
      val mongoRes: SingleObservable[DeleteResult] = mongo.deleteOne(equal("_id", key))
      val r = adapters.observableAsPublisher(mongoRes)
      r
    }

    override def delete(key: String): ReactivePublisher[DeleteResultType] = {
      doDelete(key)
    }
  }

}