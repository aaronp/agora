package streaming.api

import streaming.rest.WebURI

class WebURITest extends BaseStreamingApiSpec {

  "WebURI.apply" should {
    "parse /foo/bar" in {
      WebURI.get("/foo/bar").resolve() shouldBe Right(List("foo", "bar"))
    }
    "parse user/:id/:name/prefs" in {
      WebURI.get("user/:id/:name/prefs").resolve(Map("id" -> "123", "name" -> "anybody")) shouldBe Right(List("user", "123", "anybody", "prefs"))
    }
  }
}
