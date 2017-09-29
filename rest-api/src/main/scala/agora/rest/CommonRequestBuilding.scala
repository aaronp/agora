package agora.rest

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.HttpEncodings._
import akka.http.scaladsl.model.headers.`Accept-Encoding`

trait CommonRequestBuilding extends RequestBuilding {

  implicit class RichHttpMessage(msg: HttpRequest) {
    def withCommonHeaders: HttpRequest = {
      // TODO
      msg //.withHeaders(CommonRequestBuilding.EncodingHeaders)
    }
  }

}

object CommonRequestBuilding {

  val EncodingHeaders: `Accept-Encoding` = `Accept-Encoding`(gzip, chunked, compress, deflate, identity)

}
