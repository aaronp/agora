package jabroni.api

import jabroni.api.client.SubmitJob
import jabroni.api.exchange.JobPredicate
import jabroni.api.json.JPredicate

trait Implicits extends
  SubmitJob.LowPriorityImplicits with
  JobPredicate.LowPriorityImplicits with
  JPredicate.LowPriorityImplicits

object Implicits extends Implicits
