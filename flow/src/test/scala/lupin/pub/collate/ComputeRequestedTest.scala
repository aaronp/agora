package lupin.pub.collate

import lupin.BaseFlowSpec

class ComputeRequestedTest extends BaseFlowSpec {

  "ComputeRequested.sort" should {
    "sort the map by increasing nur of selected" in {

      val map: Map[Int, Long]  = Map(1 -> 10, 2 -> 31, 3 -> 2, 4 -> 3, 5 -> 100)
      val (total, max, sorted) = ComputeRequested.sort(map.iterator)
      total shouldBe map.size
      max shouldBe 100
      sorted.key shouldBe 3
      sorted.next.key shouldBe 4
      sorted.next.next.key shouldBe 1
      sorted.next.next.next.key shouldBe 2
      sorted.next.next.next.next.key shouldBe 5
      sorted.next.next.next.next.next shouldBe null
    }
  }
  "ComputeRequested.disperseRequested" should {

    List(
      TestCase(1, List(1), 0), // one subscription, 0 requested, w/ new request for 1
      TestCase(1, List(2), 1), // one subscription, 1 requested, w/ new request for 1
      TestCase(10, List(12), 2), // one subscription, 2 requested, w/ new request for 10
      TestCase(1, List(1, 1, 2, 3, 4), 0, 1, 2, 3, 4),
      TestCase(2, List(2, 1, 2, 3, 4), 0, 1, 2, 3, 4),
      TestCase(3, List(2, 2, 2, 3, 4), 0, 1, 2, 3, 4),
      TestCase(4, List(3, 2, 2, 3, 4), 0, 1, 2, 3, 4),
      TestCase(5, List(3, 3, 2, 3, 4), 0, 1, 2, 3, 4),
      TestCase(6, List(3, 3, 3, 3, 4), 0, 1, 2, 3, 4),
      TestCase(7, List(4, 3, 3, 3, 4), 0, 1, 2, 3, 4)
    ).foreach { tc =>
      import tc.requested

      s"distribute a request for $requested across ${tc.subs} as ${tc.expctd}" in {
        val updates: Map[Int, Long] = ComputeRequested(tc.input, requested, true)

        val newState = updates.foldLeft(tc.input.toMap) {
          case (map, (id, r)) => map.updated(id, map(id) + r)
        }
        newState shouldBe tc.expectedMap
      }
    }
  }

  case class TestCase(requested: Long, expected: List[Long], currentState: Long*) {
    def input = currentState.zipWithIndex.map(_.swap).iterator

    def expectedMap = expected.zipWithIndex.map(_.swap).toMap

    def format(m: Map[Int, Long]) = {
      m.map {
          case (id, r) => s"sub$id requesting $r"
        }
        .mkString(", ")
    }

    def subs = format(input.toMap)

    def expctd = format(expectedMap)
  }

}
