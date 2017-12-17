package agora.api.exchange

import agora.api.json.{JExpression, JPath}

/**
  * Represents an update which should be made to the [[agora.api.worker.WorkerDetails]] when a job is matched against
  * a [[WorkSubscription]].
  *
  * Each matched worker will have its json update with the result of the [[agora.api.json.JExpression]] at
  * the given [[agora.api.json.JPath]]
  */
case class OnMatchUpdateAction(expression: JExpression, path: JPath)
