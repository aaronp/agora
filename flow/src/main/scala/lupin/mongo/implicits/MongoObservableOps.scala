package lupin.mongo.implicits

import lupin.mongo.adapters._
import org.reactivestreams.Publisher

class MongoObservableOps[T](obs: org.mongodb.scala.Observable[T]) {
  def toReactive: Publisher[T] = observableAsPublisher(obs)
}
