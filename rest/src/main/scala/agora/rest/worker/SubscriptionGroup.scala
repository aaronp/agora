package agora.rest.worker

import agora.api.exchange.{Exchange, WorkSubscription, WorkSubscriptionAck}
import agora.api.worker.SubscriptionKey
import akka.http.scaladsl.util.FastFuture
import com.typesafe.config.{Config, ConfigList}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A subscription group represents work subscriptions which will reference each other.
  *
  * @param subscriptions
  * @param initialExecutionSubscription
  */
case class SubscriptionGroup(subscriptions: List[WorkSubscription], initialExecutionSubscription: Int) {

  /**
    * Creates the subscriptions on the exchange
    *
    * Subsequent subscription references the other, so any requests sent to one will decrement the 'take' count
    * for the other.
    *
    * @return the subscription ids
    */
  def createSubscriptions(exchange: Exchange)(implicit ec: ExecutionContext): Future[List[SubscriptionKey]] = {

    subscriptions match {
      case Nil => Future.successful(Nil)
      case head :: tail =>
        import akka.http.scaladsl.util.FastFuture._
        exchange.subscribe(head).fast.flatMap {
          case WorkSubscriptionAck(firstSubscription) =>
            val subsequentSubscriptions = tail.map(_.addReference(firstSubscription)).map(exchange.subscribe)
            val ackListFuture           = FastFuture.sequence(subsequentSubscriptions)

            ackListFuture.flatMap { (acks: List[WorkSubscriptionAck]) =>
              val ids: List[SubscriptionKey] = firstSubscription :: acks.map(_.id)

              exchange.take(firstSubscription, initialExecutionSubscription).map { _ =>
                ids
              }
            }
        }
    }
  }

}

object SubscriptionGroup {

  /**
    * Represents a list containing work subscriptions which should share the same subscription reference
    *
    * Consider a groupList representing 'mySubscriptionGroups' below:
    * {{{
    *   mySubscriptionGroups : [
    *
    *      # group one
    *      [${lightWorkItem1}, ${lightWorkItem2}],
    *
    *      # group two
    *      [${singleHeavyItem}]
    *
    *   ]
    * }}}
    *
    * where 'lightWorkItem1', 'lightWorkItem2' and 'singleHeavyItem' are assumed to be subscriptions declared elsewhere
    * in the configuration, such as :
    *
    * {{{
    * # take the base subscription settings
    * lightWorkItem1 = ${subscription}
    * lightWorkItem1 {
    *   details: {
    *     runUser: ${USER}
    *     path: "rest/worker/light1"
    *     name: "light work item"
    *   }
    *   jobCriteria: "match-all"
    *   submissionCriteria: "match-all"
    *   subscriptionReferences : []
    * }
    * }}}
    *
    * The intention is for each group to share a subscription reference, so 'lightWorkItem1' and 'lightWorkItem2' will [[agora.api.exchange.Exchange.take]]
    * from the same subscription ID.
    *
    * 'singleHeavyItem' sits on its own, and so doesn't use a subscription reference unless it has declared one itself
    *
    * The group's subscription reference is simply concatenated with any already declared subscriptionReferences,
    * so if a member of a group already contains subscription references, it will also contain the group's subscription
    * reference.
    */
  def apply(config: Config, groupsName: String) = {
    import scala.collection.JavaConverters._

    val groupsList: ConfigList = config.getList(groupsName)
    groupsList.asScala.toList
  }
}
