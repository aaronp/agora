package jabroni.api

import jabroni.api.client.SubmitJob
import jabroni.api.exchange.JobPredicate

trait Implicits extends
  SubmitJob.LowPriorityImplicits with
  JobPredicate.LowPriorityImplicits

object Implicits extends Implicits
