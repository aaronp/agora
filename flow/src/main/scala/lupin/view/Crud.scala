package lupin.view

import lupin.mongo.adapters
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.result.{DeleteResult, UpdateResult}
import org.mongodb.scala.{Completed, MongoCollection, SingleObservable}
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
    def asBson(value: T): Bson
  }

  def apply[T: AsBson](mongo: MongoCollection[T]): Crud[String, T] = new Crud[String, T] {
    val bsonConverter = implicitly[AsBson[T]]
    override type CreateResultType = Completed
    override type UpdateResultType = UpdateResult
    override type DeleteResultType = DeleteResult

    override def create(key: String, value: T): ReactivePublisher[CreateResultType] = {
      val doc: Bson = bsonConverter.asBson(value)
      val mongoRes = mongo.insertOne(value)
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