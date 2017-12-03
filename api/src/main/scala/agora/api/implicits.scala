package agora.api

import agora.api.exchange.JobPredicate
import agora.api.exchange.dsl.LowPriorityCirceSubmitable
import agora.api.json.{AgoraJsonImplicits, JPredicate}
import agora.api.streams.PublisherOps
import agora.io.dao.HasId

// format: off
trait Implicits extends
  LowPriorityCirceSubmitable with
  JobPredicate.LowPriorityImplicits with
  JPredicate.LowPriorityPredicateImplicits with
  AgoraJsonImplicits with
  PublisherOps.LowPriorityPublisherImplicits with
  HasId.LowPriorityHasIdImplicits
// format: on

object Implicits extends Implicits
