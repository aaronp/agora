package jabroni.api.exchange

import jabroni.api.client.SubmitJob
import jabroni.api.json.JMatcher

trait JobPredicate {
  def matches(offer: SubmitJob, work: WorkSubscription): Boolean
}

object JobPredicate {

  object JsonJobPredicate extends JobPredicate {
    override def matches(offer: SubmitJob, work: WorkSubscription): Boolean = {
      val offerMatcher: JMatcher = offer.submissionDetails.workMatcher
      val workMatcher = work.workMatcher
      offerMatcher.matches(work.details.aboutMe) &&
        workMatcher.matches(offer.job)
    }
  }

  trait LowPriorityImplicits {
    implicit def matcher: JobPredicate = apply()
  }

  object Implicits extends LowPriorityImplicits

  def apply(): JobPredicate = JsonJobPredicate

}
