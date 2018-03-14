package agora.api

import agora.api.config.JsonConfig
import agora.api.data.LowPriorityDataImplicits
import agora.api.exchange.JobPredicate
import agora.api.exchange.dsl.LowPriorityCirceSubmitable
import agora.api.json.JsonApiImplicits
import agora.api.streams.{JsonFeedDsl, PublisherOps}
import agora.io.dao.HasId
import agora.json.AgoraJsonImplicits
import agora.time.TimeLowPriorityImplicits

// format: off
trait Implicits extends
  LowPriorityCirceSubmitable with
  JobPredicate.LowPriorityImplicits with
  AgoraJsonImplicits with
  PublisherOps.LowPriorityPublisherImplicits with
  HasId.LowPriorityHasIdImplicits with
  JsonConfig.LowPriorityConfigImplicits with
  JsonFeedDsl.LowPriorityJsonFeedImplicits with
  LowPriorityDataImplicits with
  TimeLowPriorityImplicits with
  JsonApiImplicits
// format: on

object Implicits extends Implicits
