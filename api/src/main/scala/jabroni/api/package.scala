package jabroni

import java.util.UUID

import io.circe.Encoder


package object api {

  type User = String
  type JobId = UUID
  type SubscriptionKey = UUID

  def nextSubscriptionKey() : SubscriptionKey= UUID.randomUUID()

  def nextJobId() : JobId = UUID.randomUUID()

}
