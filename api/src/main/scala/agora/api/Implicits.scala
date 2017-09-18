package agora.api

import agora.api.exchange.{JobPredicate, SubmitJob}
import agora.api.json.{JPredicate, JsonByteImplicits}

trait Implicits extends SubmitJob.LowPriorityImplicits with JobPredicate.LowPriorityImplicits with JPredicate.LowPriorityPredicateImplicits with JsonByteImplicits

object Implicits extends Implicits
