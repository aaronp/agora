package agora.rest

import java.util.UUID

package object ws {
  type MsgId = String
  def nextMsgId(): MsgId = UUID.randomUUID().toString
}
