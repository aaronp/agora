package agora

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult

package object rest {

  /**
    * Adds conversions from basic strings to [[StringOps]] to e.g. produce
    * http headers based on strings
    */
  trait HttpHeaderImplicits {
    implicit def asRichKey(key: String) = new StringOps(key)
  }

  /**
    * Exposes both the agora.api.Implicits and all the agora.rest.* implicits in a single importable
    * trait
    */
  trait RestImplicits extends agora.api.Implicits with RestConversionImplicits with HttpHeaderImplicits

  object RestImplicits extends RestImplicits

  // TODO - have this extend RestImplicits
  object implicits extends RestConversionImplicits with HttpHeaderImplicits

  class StringOps(key: String) {
    def asHeader(value: String): HttpHeader = {
      HttpHeader.parse(key, value) match {
        case ParsingResult.Ok(h, Nil) => h
        case res =>
          val msg = s"Couldn't create header '$key'=>$value<  -->\n" + res.errors.mkString(";")
          throw new Exception(msg)
      }
    }
  }

}
