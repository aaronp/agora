package jabroni

import java.util.UUID


package object api {

  type User = String
  type JobId = UUID

  type WorkRequestId = UUID
  def nextWorkId() = UUID.randomUUID()
  def nextJobId() = UUID.randomUUID()
}
