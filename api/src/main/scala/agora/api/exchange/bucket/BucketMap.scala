package agora.api.exchange.bucket

import agora.api.exchange.{Requested, WorkSubscription}
import agora.json.JPath
import agora.api.worker.SubscriptionKey
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

/**
  * A work subscription bucket groups work subscriptions based on a set of key paths which are used to select
  * values in the [[agora.api.exchange.WorkSubscription]]s' [[agora.api.worker.WorkerDetails]].
  *
  * A bucket can be created explicitly or implicitly by being specified by a [[agora.api.exchange.SubmitJob]]
  *
  */
case class BucketMap(workerBucket: BucketPathKey,
                     subscriptionsByKey: Map[BucketValueKey, Set[SubscriptionKey]] = Map(),
                     keysBySubscription: Map[SubscriptionKey, Set[BucketValueKey]] = Map())
    extends StrictLogging {

  def containsSubscription(id: SubscriptionKey) = keysBySubscription.contains(id)

  private def extractKey(subscriptionDetails: Json): Option[List[Json]] = {
    val (key, isValid) = workerBucket.foldRight((List[Json](), true)) {
      case (_, pair @ (_, false)) => pair
      case (BucketKey(path, optional), (keyValues, true)) =>
        path(subscriptionDetails) match {
          case Some(jsonValue) => (jsonValue :: keyValues) -> true
          case None            => (Json.Null :: keyValues) -> optional
        }
    }
    if (isValid) {
      Option(key)
    } else {
      None
    }
  }

  /**
    * Update the bucket based on the updated subscription details
    *
    * @param subscriptionKey
    * @param subscriptionDetails
    */
  def update(subscriptionKey: SubscriptionKey, subscriptionDetails: Json): BucketMap = {
    val keyOpt = extractKey(subscriptionDetails)
    keyOpt match {
      case Some(jsonKey) =>
        val newList               = subscriptionsByKey.getOrElse(jsonKey, Set.empty) + subscriptionKey
        val newSubscriptionsByKey = subscriptionsByKey.updated(jsonKey, newList)

        val newBucketKeys: Set[BucketValueKey] = keysBySubscription.getOrElse(subscriptionKey, Set.empty) + jsonKey
        val newKeysBySubscription              = keysBySubscription.updated(subscriptionKey, newBucketKeys)
        copy(subscriptionsByKey = newSubscriptionsByKey, keysBySubscription = newKeysBySubscription)
      case None =>
        if (keysBySubscription.contains(subscriptionKey)) {
          logger.debug(s"Removing Subscription '${subscriptionKey}' from ${workerBucket}")
          val newKeysBySubscription = keysBySubscription - subscriptionKey
          val newSubscriptionsByKey = subscriptionsByKey.flatMap {
            case (key, values) =>
              val newSet = values - subscriptionKey
              if (newSet.isEmpty) {
                None
              } else {
                Option(key -> newSet)
              }
          }
          copy(subscriptionsByKey = newSubscriptionsByKey, keysBySubscription = newKeysBySubscription)
        } else {
          logger.debug(s"Subscription '${subscriptionKey}' does not match ${workerBucket}")
          this
        }
    }
  }

  /**
    * @param valueKeys
    * @return the subscription keys for the given values
    */
  def workSubscriptionKeysForWorkerMatchBucket(valueKeys: List[Json]): Option[Set[SubscriptionKey]] = {
    subscriptionsByKey.get(valueKeys)
  }
}

object BucketMap {
  def apply(jpaths: JPath*): BucketMap = {
    val keys = jpaths.map { path =>
      BucketKey(path, false)
    }
    val bpk: BucketPathKey = keys.toList
    apply(bpk, Map[SubscriptionKey, (WorkSubscription, Requested)]())
  }

  def apply(keys: BucketPathKey, subscriptionsById: Map[SubscriptionKey, (WorkSubscription, Requested)]): BucketMap = {
    subscriptionsById.foldLeft(BucketMap(keys)) {
      case (bucketMap, (key, (subscription, _))) =>
        bucketMap.update(key, subscription.details.aboutMe)
    }
  }
}
