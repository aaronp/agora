package jabroni.domain

import org.scalatest.{Matchers, WordSpec}

class TakeTest extends WordSpec with Matchers {
  "Take that" should {
    "consume n items" in {

      val in: List[(Int, String)] = List(2 -> "two", 1 -> "one", 5 -> "five")
      val (took, remaining) = Take[String, List[(Int, String)]](4, in)
      took should contain only(2 -> "two", 1 -> "one", 1 -> "five")
      remaining should contain only (4 -> "five")
    }
  }

}
