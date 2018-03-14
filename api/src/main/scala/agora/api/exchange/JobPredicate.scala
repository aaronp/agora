package agora.api.exchange

import agora.json.JPredicate
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

/**
  * Exposes the course-grained signature of pairing up jobs with worker subscriptions.
  *
  * In practice there is just one implementation which evaluates the json matchers of
  * the [[SubmitJob]] and [[WorkSubscription]], but in theory you could implement any test
  * to pair up jobs with work subscriptions
  */
trait JobPredicate {

  /**
    * @param offer the submitted job
    * @param work the work subscription candidate
    * @param requested the number of work items requested for the subscription.
    * @return true if the job should match the subscription and (ideally) that the requested is greater than zero
    */
  def matches(offer: SubmitJob, work: WorkSubscription, requested: Int): Boolean
}

object JobPredicate extends StrictLogging {

  /**
    * The json-based matching logic
    */
  object JsonJobPredicate extends JobPredicate with StrictLogging {

    override def matches(job: SubmitJob, subscription: WorkSubscription, requested: Int): Boolean = {
      val offerMatcher: WorkMatcher      = job.submissionDetails.workMatcher
      val submissionCriteria: JPredicate = subscription.submissionCriteria
      val jobCriteria                    = subscription.jobCriteria

      val subscriptionDetails: Json = subscription.matchJson(requested)

      logger.debug(s"""
           | == JOB MATCHES WORKER (${offerMatcher.criteria.matches(subscriptionDetails)}) ==
           | $offerMatcher
           | with
           | ${subscription.details.aboutMe.spaces4}
           |
           | == SUBSCRIPTION MATCHES JOB (${jobCriteria.matches(job.job)}) ==
           | $jobCriteria
           | with
           | ${job.job.spaces4}
           |
           | == SUBSCRIPTION MATCHES JOB DETAILS (${submissionCriteria.matches(job.submissionDetails.aboutMe)}) ==
           | $submissionCriteria
           | with
           | ${job.submissionDetails.aboutMe.spaces4}
           |
         """.stripMargin)

      jobSubmissionDetailsMatchesWorkSubscription(job.submissionDetails, subscriptionDetails) &&
      workSubscriptionMatchesJob(subscription, job.job) &&
      workSubscriptionMatchesJobDetails(subscription, job.submissionDetails)
    }
    def workSubscriptionMatchesJobDetails(subscription: WorkSubscription, submissionDetails: SubmissionDetails) = {
      subscription.submissionCriteria.matches(submissionDetails.aboutMe)
    }

    def workSubscriptionMatchesJob(subscription: WorkSubscription, job: Json) = {
      subscription.jobCriteria.matches(job)
    }

    def jobSubmissionDetailsMatchesWorkSubscription(submissionDetails: SubmissionDetails, subscriptionDetails: Json) = {
      submissionDetails.workMatcher.criteria.matches(subscriptionDetails)
    }

  }

  trait LowPriorityImplicits {
    implicit def matcher: JobPredicate = apply()
  }

  def apply() = JsonJobPredicate

}
