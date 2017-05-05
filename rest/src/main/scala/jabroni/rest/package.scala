package jabroni

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult

package object rest {


  object implicits {
    implicit class RichKey(val key: String) extends AnyVal {
      def asHeader(value: String): HttpHeader = {
        HttpHeader.parse(key, value) match {
          case ParsingResult.Ok(h, Nil) => h
          case res => throw new Exception(res.errors.mkString(";"))
        }
      }
    }

  }
}
