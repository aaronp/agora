package lupin.pub.query

import lupin.BaseFlowSpec
import lupin.example.IndexSelection

class QueryPublisherTest extends BaseFlowSpec {

  "QueryPublisher" should {
    "notify subscribers of indices" in {

      val qp = QueryPublisher[String](IndexSelection(0, 3))
    }
  }
}
