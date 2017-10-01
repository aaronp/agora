package agora.api.exchange

import agora.api.`match`.MatchDetails
import agora.api.worker.WorkerDetails

/** A client-side representation of a [[SubmitJob]] / [[WorkerDetails]] match produced by an [[Exchange]], created
  *
  * by the [[AsClient]] which made the initial request.
  *
  * The workflow is:
  *
  * 1) Some custom request object 'Foo' ->
  * 2) a 'SubmitJob' containing the json form for 'Foo' ->
  * 3) a call to an Exchange with the SubmitJob ->
  * 4) a reply w/ a match from the exchange ->
  * 5) the exchnage client taking initial Foo, SubmitJob, and MatchDetails/WorkerDetails
  * from the match response and producing a Dispatch
  * 6) an implicit AsClient typeclass using the info in 'Dispatch' to make some request, presumably against the
  * worker defined in 'WorkerDetails'
  *
  * @param request       the initial request object used to produce the SubmitJob
  * @param job           the [[SubmitJob]] instance which was produced from request
  *                      request and sent to the exchange
  * @param matchDetails  the match details from the [[Exchange]]
  * @param matchedWorker the worker details matched to the [[SubmitJob]]
  * @tparam T the original request type before it was serialised into json in the submit job
  */
case class Dispatch[+T](request: T, job: SubmitJob, matchDetails: MatchDetails, matchedWorker: WorkerDetails) {
  def path = matchedWorker.path

  def location = matchedWorker.location
}
