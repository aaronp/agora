package lupin.mongo

import lupin.BaseFlowSpec

class ParsedMongoTest extends BaseFlowSpec {

  "ParsedMongo.load" should {
    "connect to a locally running mongo" in {
      val pm = ParsedMongo.load()
      try {
        val c     = pm.defaultCollection
        val total = c.count().headOption().futureValue
        total should not be empty

      } finally {
        pm.close()
      }
    }
  }
}
