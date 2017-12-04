package layout

import org.scalatest.{FunSuite, Matchers, WordSpec}

/**
  * Copyright (c) 2017 HSBC plc. This software is the proprietary information of HSBC Plc. All Right Reserved.
  */
class LayoutTest extends WordSpec with Matchers {

  import Layout.syntax._

  "Rectangle.contains" should {
    "return true for points within the rectangle" in {
      val rect = Rectangle(-2, -3, 4, 6)
      rect.x2 shouldBe 2
      rect.y2 shouldBe 3

      rect.contains(-3, 0) shouldBe false
      rect.contains(-2, -4) shouldBe false
      rect.contains(2, 4) shouldBe false
      rect.contains(3, 2) shouldBe false
      rect.contains(0, 0) shouldBe true
    }

    for {
      x <- (-2 to 2)
      y <- (-3 to 3)
    } {
      val rect = Rectangle(-2, -3, 4, 6)
      s"return true for ($x, $y) in $rect" in {
        rect.contains(x, y) shouldBe true
      }
    }
  }
  "lines" should {

    "render" in {
      val line1 = Line(10, 10, 50, 30)
      val line2 = Line(5, 30, 15, 0)

      val view = Rectangle(-2, -2, 100, 100)

      val res1: AsciiRows = line1.layout(view)
      val res2            = line2.layout(view)

      val x = res1.render()
      println(x)
      println(res2.render())

    }
  }

}
