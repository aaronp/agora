package jabroni.api

import jabroni.api.client.SubmitJob
import jabroni.api.exchange.Matcher

trait Implicits extends
  SubmitJob.LowPriorityImplicits with
  Matcher.LowPriorityImplicits

object Implicits extends Implicits
