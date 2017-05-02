package jabroni

import java.util.UUID


package object api {

  type User = String
  type JobId = UUID
  type SubscriptionKey = UUID

  type WorkRequestId = UUID
  def nextSubscriptionId() = UUID.randomUUID()
  def nextJobId() = UUID.randomUUID()
}
