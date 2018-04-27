package agora.api.streams

import agora.BaseApiSpec
import agora.json.{ArrayType, BooleanType, DiffEntry, JPath, JsonDiff, JsonSemigroup, NumericType}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import lupin.ListSubscriber

/**
  * In practice people may start w/ a 'DataFeedDsl', marshalling their 'T : Encoder' types into a JsonFeedDsl
  */
class JsonFeedDslTest extends BaseApiSpec {

  case class Meh(theKey: String)

  case class Data(path: List[Meh])

  val data1    = Data(List(Meh("cold"), Meh("warmer"), Meh("red hot!"), Meh("too far")))
  val dataNope = Data(List(Meh("I don't have a big enough array :-(")))
  val data2    = Data(List(Meh("uno"), Meh("dos"), Meh("tres")))

  "JsonFeedDsl.withDeltas" should {
    "send json deltas" in {
      val publisher = BaseProcessor.withMaxCapacity[Json](10)
      val feed      = JsonFeedDsl(publisher)

      val deltas             = feed.withDeltas()
      val deltasSubscription = new ListSubscriber[JsonDiff]()
      deltasSubscription.request(10)
      deltas.subscribe(deltasSubscription)

      // push an initial message
      publisher.onNext(json"""{ "msgId" : 1, "first" :true }""")
      val firstDiff = JsonDiff(json"""{ "msgId" : 1, "first" :true }""")
      deltasSubscription.received() shouldBe List(firstDiff)

      // push another message - we should be alerted of the diff
      publisher.onNext(json"""{ "msgId" : 2, "second" :true  }""")
      val List(JsonDiff(secondDiffs), `firstDiff`) = deltasSubscription.received()
      secondDiffs should contain allOf (DiffEntry(
        List("msgId"),
        Json.fromInt(1),
        Json.fromInt(2)
      ),
      DiffEntry(
        List("first"),
        Json.fromBoolean(true),
        Json.Null
      ),
      DiffEntry(
        List("second"),
        Json.Null,
        Json.fromBoolean(true)
      ))
    }
  }
  "JsonFeedDsl.withFields" should {
    "expose the json fields of the data going through it" in {
      val publisher = BaseProcessor.withMaxCapacity[Json](10)

      val fieldsSubscription = JsonFeedDsl(publisher).withFields()
      fieldsSubscription.request(1)

      publisher.onNext(json"""[]""")
      fieldsSubscription.fields shouldBe Vector(Nil -> ArrayType)

      publisher.onNext(json"""42""")
      fieldsSubscription.fields should contain only (Nil -> ArrayType)
      fieldsSubscription.request(1)
      fieldsSubscription.fields should contain only (Nil -> ArrayType, Nil -> NumericType)

      publisher.onNext(json""" { "hello" : { "world" : true } }""")
      fieldsSubscription.request(1)
      fieldsSubscription.fields should contain only (Nil -> ArrayType, Nil -> NumericType, List("hello", "world") -> BooleanType)

      publisher.onNext(json""" { "hello" : { "there" : 12} }""")
      fieldsSubscription.request(1)
      fieldsSubscription.fields should contain only (Nil -> ArrayType, Nil -> NumericType, List("hello", "world") -> BooleanType, List("hello", "there") -> NumericType)

    }
  }
  "JsonFeedDsl.indexOnKeys" should {
    "conflate values published between subscriber requests" in {
      val publisher = BaseProcessor.withMaxCapacity[Json](10)
      val feed      = JsonFeedDsl(publisher)

      var queueCreationCount = 0

      // produce a feed which uses a conflating queue
      val conflatingPriceByRIC = feed.indexOnKeys(JPath("ric")) {
        queueCreationCount = queueCreationCount + 1
        ConsumerQueue[Json](None)(JsonSemigroup)
      }

      //
      // 1) create a subscription for 'foo' data
      //
      queueCreationCount shouldBe 0
      val fooListener = new ListSubscriber[Json]()
      conflatingPriceByRIC.getPublisher("foo").subscribe(fooListener)
      queueCreationCount shouldBe 1

      //
      // 2) push some initial 'foo' data through
      //
      publisher.onNext(json"""{ "ric" : "foo", "price" : 12.34, "first" : true }""")
      publisher.onNext(json"""{ "ric" : "bar", "price" : 100, "weWontEverSeeThis" : true }""")

      //
      // 3) verify we see the pushed data when our subscription requests it
      //
      fooListener.received() shouldBe Nil
      fooListener.request(1)
      fooListener.received() shouldBe List(json"""{ "ric" : "foo", "price" : 12.34, "first" : true }""")
      queueCreationCount shouldBe 1

      //
      // 4) exercise the test -- push TWO updates before 'fooListener' requests the next value.
      //
      publisher.onNext(json"""{ "ric" : "foo", "price" : 34.56, "second" : true }""")
      publisher.onNext(json"""{ "ric" : "foo", "price" : 78.9, "third" : true }""")

      //
      // 5) When our listener next requests (pulls) data, it should see a single conflated update
      fooListener.request(2) // allow our test to pull 2 values if possible to ensure we fail if we get both
      fooListener.received() shouldBe List(
        json"""{ "ric" : "foo", "price" : 78.9, "first" : true, "second" : true , "third" : true  }""",
        json"""{ "ric" : "foo", "price" : 12.34, "first" : true }"""
      )

    }
    "route the feed based on the values for the given jpath for the IndexSubscriber" in {
      val publisher = BaseProcessor.withMaxCapacity[Json](10)
      val feed      = JsonFeedDsl(publisher)

      var queueCreationCount = 0

      // call the method under test - create an IndexSubscriber
      val byKey = feed.indexOnKeys(JPath.forParts("path", "2", "theKey")) {
        queueCreationCount = queueCreationCount + 1
        ConsumerQueue.withMaxCapacity(10)
      }

      val redHotListener = new ListSubscriber[Json]()
      byKey.getPublisher("red hot!").subscribe(redHotListener)
      queueCreationCount shouldBe 1

      val tresListener = new ListSubscriber[Json]()
      byKey.getPublisher("tres").subscribe(tresListener)
      queueCreationCount shouldBe 2

      // squirt some data through the publisher
      publisher.onNext(data1.asJson)
      publisher.onNext(dataNope.asJson)
      publisher.onNext(data2.asJson)
      queueCreationCount shouldBe 2

      withClue("subscriptions shouldn't get any data until they request it") {
        redHotListener.received() shouldBe Nil
        tresListener.received() shouldBe Nil
      }

      withClue("subscriptions should get only the data for the given key") {
        redHotListener.request(10)
        tresListener.request(10)
        redHotListener.received() shouldBe List(data1.asJson)
        tresListener.received() shouldBe List(data2.asJson)
      }
    }
  }
}
