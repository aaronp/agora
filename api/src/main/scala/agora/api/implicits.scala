package agora.api

import agora.api.exchange.{JobPredicate, SubmitJob, Submitable}
import agora.api.json.{AgoraJsonImplicits, JPredicate}

trait Implicits extends Submitable.LowPriorityCirceSubmitable with JobPredicate.LowPriorityImplicits with JPredicate.LowPriorityPredicateImplicits with AgoraJsonImplicits

object Implicits extends Implicits
