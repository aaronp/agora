package agora.rest.stream.registry

import agora.BaseSpec

class StreamRegistryTest extends BaseSpec {

  "StreamRegistry.snapshot" should {
    "produce a snapshot summary of the current queue sizes and pending requested data for a given key" in {
      val reg = new StreamRegistry
      ???
    }
  }

  "StreamRegistry.subscriber(key).flow" should {
    "request values only when an explicit TakeNext ClientSubscriptionMessage is received" in {
      ???
    }
  }
  "StreamRegistry.publisher(key).flow" should {
    "publisher value only when an explicit TakeNext ClientSubscriptionMessage is received" in {
      ???
    }
  }
  "StreamRegistry.registerPublisher" should {

    "allow subscribers to connect w/ the same key" in {
      ???
    }
    "connect previously connected subscribers which have already registered w/ the same key" in {
      ???
    }
    "error if a publisher has already been registered w/ the same key" in {
      ???
    }
  }
}
