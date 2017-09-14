package agora.api

import agora.api.exchange.{JobPredicate, SubmitJob}
import agora.api.json.JPredicate

trait Implicits extends SubmitJob.LowPriorityImplicits with JobPredicate.LowPriorityImplicits with JPredicate.LowPriorityPredicateImplicits

object Implicits extends Implicits
