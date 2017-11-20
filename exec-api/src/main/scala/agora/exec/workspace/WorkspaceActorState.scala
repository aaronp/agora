package agora.exec.workspace

import akka.actor.Cancellable

case class WorkspaceActorState(pendingRequests: Set[AwaitUploads], nextCheck: Option[Cancellable]) {
  def remove(msg: AwaitUploads) = {
    copy(pendingRequests - msg)
  }
}
