package agora.api.exchange.bucket

import agora.BaseApiSpec
import agora.json.JPath
import io.circe.Json

class BucketMapTest extends BaseApiSpec {

  "BucketMap.update" should {

    "add subscription data which which does not contain optional fields" in {

      val map = BucketMap(List(BucketKey(JPath("id"), false), BucketKey(JPath("favoriteColor"), true)))

      map.update("is valid", json"""{ "id" : "meh", "favoriteColor" : "blue" }""").containsSubscription("is valid") shouldBe true
      map.update("no color", json"""{ "id" : "meh"}""").containsSubscription("no color") shouldBe true
      map.update("no id", json"""{ "favoriteColor" : "meh"}""").containsSubscription("no id") shouldBe false

    }

    "not add subscription data which does not match all jpaths" in {
      val map   = BucketMap(JPath("user"), JPath("session"))
      val after = map.update("some subscription", json"""{ "user" : "dave" }""")
      after shouldBe map
      after.subscriptionsByKey.isEmpty shouldBe true
      after.keysBySubscription.isEmpty shouldBe true
    }
    "add subscription data which matches the jpaths" in {
      val map   = BucketMap(JPath("user"), JPath("session"))
      val after = map.update("some subscription", json"""{ "user" : "dave", "session" : 123, "meh" : 1 }""")
      after.subscriptionsByKey.keySet should contain only (List(Json.fromString("dave"), Json.fromInt(123)))
      after.keysBySubscription.keySet should contain only ("some subscription")
    }
    "remove subscription data which no longer matches the jpaths" in {

      val map     = BucketMap(JPath("user"), JPath("session"))
      val updated = map.update("some subscription", json"""{ "user" : "dave", "session" : 123, "meh" : 1 }""")
      updated.containsSubscription("some subscription") shouldBe true
      val emptyAgain = updated.update("some subscription", json"""{ "user" : "dave", "noSession" : 123, "meh" : 1 }""")
      emptyAgain shouldBe map
      emptyAgain.subscriptionsByKey.isEmpty shouldBe true
      emptyAgain.keysBySubscription.isEmpty shouldBe true
      emptyAgain.containsSubscription("some subscription") shouldBe false
    }
  }

}
