package agora.api.exchange

import agora.api.exchange.dsl.LowPriorityCirceSubmitable

/**
  * The ability to create [[SubmitJob]] instances from some value
  *
  * @tparam T the type which will be represented by the [[SubmitJob]]'s 'job' json value
  */
trait Submitable[T] {

  /**
    * @return the input request T into a SubmitJob
    */
  def asSubmitJob(request: T): SubmitJob

  /** @param f
    * @return a new submitable with the updated submission details
    */
  def withDetails(f: SubmissionDetails => SubmissionDetails): Submitable[T] = {
    val parent = this
    new Submitable[T] {
      override def asSubmitJob(request: T) = {
        val job = parent.asSubmitJob(request)
        job.withDetails(f(job.submissionDetails))
      }
    }
  }
}

object Submitable {

  object implicits extends LowPriorityCirceSubmitable

  /**
    * summon an implicit instance
    *
    * @param impl
    * @tparam T
    * @return
    */
  def instance[T](implicit impl: Submitable[T]): Submitable[T] = impl

}
