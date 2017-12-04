package agora.rest

import agora.rest.exchange._

package object stream {

  type TakeNext = agora.rest.exchange.TakeNext
  val TakeNext = agora.rest.exchange.TakeNext

  type Cancel = agora.rest.exchange.Cancel.type
  val Cancel = agora.rest.exchange.Cancel
}
