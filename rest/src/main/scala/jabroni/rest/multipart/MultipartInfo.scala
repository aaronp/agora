package jabroni.rest.multipart

import akka.http.scaladsl.model.ContentType

case class MultipartInfo(fieldName: String, fileName: Option[String], contentType: ContentType)
