package jabroni.rest.server

import language.implicitConversions

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import jabroni.api._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

class BaseSpec
  extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
{


  implicit def richLedger(ledger: Ledger) = new {
    def buy[Q <% Quantity](q: Q): AtWord = new AtWord(ledger, Buy, q)

    def sell[Q <% Quantity](q: Q): AtWord = new AtWord(ledger, Sell, q)
  }

  def routes(ledger: Ledger = Ledger()): Route = JabroniRoutes(ledger).routes

  class AtWord(ledger: Ledger, typ: OrderType, quantity: Quantity) {
    def at(p: Price): Ledger = {
      ledger.placeOrder(Order("somebody", typ, quantity, p)).futureValue
      ledger
    }
  }

}
