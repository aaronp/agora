package agora.exec.workspace

import agora.exec.workspace.DependencyCheck._

/**
  * Contains the logic to determine if a workspace dependencies are satisfied
  */
object DependencyCheck {

  sealed trait DependencyState

  case object Ready extends DependencyState

  case object DoesNotExist extends DependencyState

  case class NotOfExpectedSize(actualSize: Long, expectedSize: Long) extends DependencyState

  case object NoMetadataFile extends DependencyState

}

case class DependencyCheck(dependencyStates: Map[String, DependencyState]) {
  def canRun(awaitFlushedOutput: Boolean) = dependencyStates.values.forall {
    case Ready                   => true
    case NotOfExpectedSize(_, _) => !awaitFlushedOutput
    case NoMetadataFile          => !awaitFlushedOutput
    case DoesNotExist            => false
  }

  /**
    * All events controlled by this service (uploads, started processes, etc) trigger events to reevaluate the workspace
    * dependencies.
    *
    * There is a race-condition issue, however, where dependencies would appear to be ready, but the results have not
    * yet been totally flushed.
    *
    * @return
    */
  def containsPollableResult = dependencyStates.values.exists {
    case NotOfExpectedSize(_, _) => true
    case _                       => false
  }

}
