package jabroni

import java.util.UUID

import io.circe.Encoder


package object api {

  type User = String
  type JobId = String
  type SubscriptionKey = String

  def nextSubscriptionKey() : SubscriptionKey= UUID.randomUUID().toString

  def nextJobId() : JobId = UUID.randomUUID().toString

}
