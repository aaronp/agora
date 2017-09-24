package agora.rest

import agora.BaseSpec

class RestConversionImplicitsTest extends BaseSpec with RestConversionImplicits {

  "RestConversionImplicits" should {
    "be able to produce an AsClient[HttpRequest, HttpResponse] in an implicit workerConfig is in scope" in {}
  }
}
