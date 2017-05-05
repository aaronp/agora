package jabroni.rest.test

import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time._

trait ExchangeValidation extends Matchers
  with ScalaFutures with Eventually {

  override implicit def patienceConfig = PatienceConfig(timeout = Span(1000, Seconds), interval = Span(1000, Millis))
}
