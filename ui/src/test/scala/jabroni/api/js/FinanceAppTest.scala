package jabroni.api.js

import utest._
import org.scalajs.jquery.jQuery

import scala.scalajs.js
import scala.scalajs.js.JSON

object JabroniAppTest extends TestSuite {

  // Initialize App
  //  JabroniApp.setupUI()

  def tests = TestSuite {
    'parseOrderJson {
      val orderBookJson = """{"buyTotals":[{"quantity":3,"price":306}],"sellTotals":[{"quantity":1,"price":123}]}"""
      val json: js.Dynamic = JSON.parse(orderBookJson)
      val bt = json.buyTotals

//      assert(jQuery("p:contains('Hello World')").length == 1)
    }
  }
}
