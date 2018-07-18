package streaming.api

import streaming.rest.{HttpMethod, WebURI}

class WebURITest extends BaseStreamingApiSpec {

  "WebURI.unapply" should {
    "match /index.html" in {
      WebURI.get("/index.html").unapply(HttpMethod.GET -> "/index.html") shouldBe Some(Map[String, String]())
    }
    "match index.html" in {
      WebURI.get("/index.html").unapply(HttpMethod.GET -> "index.html") shouldBe Some(Map[String, String]())
    }
    "match /foo/:int/bar/:name" in {
      WebURI.get("/foo/:int/bar/:name").unapply(HttpMethod.GET -> "/foo/123/bar/hi") shouldBe Some(Map[String, String]("int" -> "123", "name" -> "hi"))
      WebURI.get("/foo/:int/bar/:name").unapply(HttpMethod.POST -> "/foo/123/bar/hi") shouldBe None
      WebURI.get("/foo/:int/bar/:name").unapply(HttpMethod.GET -> "foo/123/bar/hi") shouldBe Some(Map[String, String]("int" -> "123", "name" -> "hi"))
      WebURI.get("/foo/:int/bar/:name").unapply(HttpMethod.GET -> "foo/bar/hi") shouldBe None
    }
  }
  "WebURI.apply" should {
    "parse /foo/bar" in {
      WebURI.get("/foo/bar").resolve() shouldBe Right(List("foo", "bar"))
    }
    "parse user/:id/:name/prefs" in {
      WebURI.get("user/:id/:name/prefs").resolve(Map("id" -> "123", "name" -> "anybody")) shouldBe Right(List("user", "123", "anybody", "prefs"))
    }
  }
}
