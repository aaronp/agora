package jabroni.api.exchange

import com.typesafe.scalalogging.StrictLogging
import jabroni.api.json.JMatcher

/**
  * Exposes the course-grained signature of pairing up jobs with worker subscriptions
  */
trait JobPredicate {
  def matches(offer: SubmitJob, work: WorkSubscription): Boolean
}

object JobPredicate extends StrictLogging {

  /**
    * The json-based matching logic
    */
  //  object JsonJobPredicate extends JobPredicate {
  //    override def matches(job: SubmitJob, subscription: WorkSubscription): Boolean = {
  //      val offerMatcher: JMatcher = job.submissionDetails.workMatcher
  //      val submissionMatcher = subscription.submissionMatcher
  //      val jobMatcher = subscription.jobMatcher
  //
  //      offerMatcher.matches(subscription.details.aboutMe) &&
  //        jobMatcher.matches(job.job) &&
  //        submissionMatcher.matches(job.submissionDetails.aboutMe)
  //    }
  //  }

  object JsonJobPredicate extends JobPredicate with StrictLogging {
    override def matches(job: SubmitJob, subscription: WorkSubscription): Boolean = {
      val offerMatcher: JMatcher = job.submissionDetails.workMatcher
      val submissionMatcher = subscription.submissionMatcher
      val jobMatcher = subscription.jobMatcher

      logger.debug(
        s"""
           | == JOB MATCHES WORKER (${offerMatcher.matches(subscription.details.aboutMe)}) ==
           | $offerMatcher
           | with
           | ${subscription.details.aboutMe.spaces4}
           |
           | == SUBSCRIPTION MATCHES JOB (${jobMatcher.matches(job.job)}) ==
           | $jobMatcher
           | with
           | ${job.job.spaces4}
           |
           | == SUBSCRIPTION MATCHES JOB DETAILS (${submissionMatcher.matches(job.submissionDetails.aboutMe)}) ==
           | $submissionMatcher
           | with
           | ${job.submissionDetails.aboutMe.spaces4}
           |
         """.stripMargin)

      offerMatcher.matches(subscription.details.aboutMe) &&
        jobMatcher.matches(job.job) &&
        submissionMatcher.matches(job.submissionDetails.aboutMe)
    }
  }

  trait LowPriorityImplicits {
    implicit def matcher: JobPredicate = apply()
  }

  object Implicits extends LowPriorityImplicits

  def apply(): JobPredicate = JsonJobPredicate

}
