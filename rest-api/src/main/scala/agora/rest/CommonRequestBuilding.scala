package agora.rest

import agora.api.`match`.MatchDetails
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.HttpEncodings._
import akka.http.scaladsl.model.headers.`Accept-Encoding`

trait CommonRequestBuilding extends RequestBuilding {

  implicit class RichHttpMessage(msg: HttpRequest) {
    def withCommonHeaders(matchDetailsOpt: Option[MatchDetails] = None): HttpRequest = {
      matchDetailsOpt.map(MatchDetailsExtractor.headersFor).fold(msg) { newHeaders =>
        // TODO - add these
        //CommonRequestBuilding.EncodingHeaders +:

        msg.withHeaders(msg.headers ++ newHeaders)
      }
    }
  }

}

object CommonRequestBuilding {

  val EncodingHeaders: `Accept-Encoding` = `Accept-Encoding`(gzip, chunked, compress, deflate, identity)

}
