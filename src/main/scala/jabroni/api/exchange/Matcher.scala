package jabroni.api.exchange

import jabroni.api.client.SubmitJob
import jabroni.api.json.JMatcher
import jabroni.api.worker.RequestWork

trait Matcher {
  def matches(offer: SubmitJob, work: RequestWork): Boolean
}

object Matcher {

  object JsonMatcher extends Matcher {
    override def matches(offer: SubmitJob, work: RequestWork): Boolean = {
      val offerMatcher: JMatcher = offer.submissionDetails.workMatcher
      val workMatcher = work.workMatcher
      offerMatcher.matches(work.worker.aboutMe) &&
        workMatcher.matches(offer.job)
    }
  }

  trait LowPriorityImplicits {
    implicit def matcher: Matcher = apply()
  }

  object Implicits extends LowPriorityImplicits

  def apply(): Matcher = JsonMatcher


  def take[T](n : Int, things : Iterable[(Int, T)]) = {
    ???
  }
}
