package agora

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

package object api {

  type User            = String
  type JobId           = String
  type SubscriptionKey = String
  type MatchId         = String

  def nextSubscriptionKey(): SubscriptionKey = UUID.randomUUID().toString

  def nextJobId(): JobId = UUID.randomUUID().toString

  def nextMatchId(): MatchId = UUID.randomUUID().toString

  def nowUTC(): ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)

  def epochUTC() = nowUTC.toLocalDateTime
}
