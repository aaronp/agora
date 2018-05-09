package lupin.pub.collate

import lupin.BaseFlowSpec

class RotatingArrayTest extends BaseFlowSpec {

  "RotatingArray" should {
    "iterate all elements" in {
      val iter = new RotatingArray[Int](List(1, 2, 3)).iterator
      iter.toList should contain allOf (1, 2, 3)
    }

    "iterate a different order each time" in {
      val ra = new RotatingArray[Int](List(1, 2, 3))
      List(
        ra.iterator.toList,
        ra.iterator.toList,
        ra.iterator.toList
      ) should contain only (List(1, 2, 3), List(2, 3, 1), List(3, 1, 2))
    }
    "iterate when elements are added or removed" in {
      val ra = new RotatingArray[Int]()
      ra.iterator.toList shouldBe Nil
      ra.add(1).iterator.toList shouldBe List(1)
      ra.iterator.toList shouldBe List(1)

      ra.add(2)

      List(
        ra.iterator.toList,
        ra.iterator.toList
      ) should contain only (List(1, 2), List(2, 1))

      ra.add(3)

      List(
        ra.iterator.toList,
        ra.iterator.toList,
        ra.iterator.toList
      ) should contain only (List(1, 2, 3), List(2, 3, 1), List(3, 1, 2))

      ra.remove(2)
      List(
        ra.iterator.toList,
        ra.iterator.toList
      ) should contain only (List(1, 3), List(3, 1))

      ra.remove(1)
      ra.remove(3)
      ra.iterator.toList shouldBe Nil
    }
  }

}
