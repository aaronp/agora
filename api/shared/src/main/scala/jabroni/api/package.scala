package jabroni

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID

import io.circe.Encoder


package object api {

  type User = String
  type JobId = String
  type SubscriptionKey = String
  type MatchId = String

  def nextSubscriptionKey() : SubscriptionKey= UUID.randomUUID().toString

  def nextJobId() : JobId = UUID.randomUUID().toString
  def nextMatchId() : MatchId = UUID.randomUUID().toString


  def nowUTC(): ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
  def epochUTC(): Long = nowUTC.toEpochSecond
}