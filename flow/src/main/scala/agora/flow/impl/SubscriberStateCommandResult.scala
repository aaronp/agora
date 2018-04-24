package agora.flow.impl

/** The state input result */
sealed trait SubscriberStateCommandResult

/** the carry-on-as-normal case */
case object ContinueResult extends SubscriberStateCommandResult

/** the stop case, either by completion or exception/error */
case class StopResult(error: Option[Throwable]) extends SubscriberStateCommandResult
case object CancelResult                        extends SubscriberStateCommandResult
