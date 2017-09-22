package agora.api

import agora.api.exchange.{JobPredicate, SubmitJob}
import agora.api.json.{JPredicate, AgoraJsonImplicits}

trait Implicits extends SubmitJob.LowPriorityImplicits with JobPredicate.LowPriorityImplicits with JPredicate.LowPriorityPredicateImplicits with AgoraJsonImplicits

object Implicits extends Implicits
