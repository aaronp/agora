package miniraft.state

import agora.BaseSpec

class MajorityTest extends BaseSpec {

  "isMajority" should {
    val values: List[(Int, Int, Boolean)] = List(
      (0, 0, true),
      (0, 1, false),
      (1, 1, true),
      (1, 2, false),
      (2, 2, true),
      (0, 3, false),
      (1, 3, false),
      (2, 3, true),
      (3, 3, true),
      (0, 4, false),
      (1, 4, false),
      (2, 4, false),
      (3, 4, true),
      (4, 4, true),
      (0, 5, false),
      (1, 5, false),
      (2, 5, false),
      (3, 5, true),
      (4, 5, true),
      (5, 5, true)
    )

    values.foreach { x =>
      val (n, total, expected) = x
      s"return $expected for $n / $total" in {
        isMajority(n, total) shouldBe expected
      }
    }
  }

}
