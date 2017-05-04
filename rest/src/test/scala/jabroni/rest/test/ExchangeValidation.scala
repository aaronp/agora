package jabroni.rest.test

import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time._

trait ExchangeValidation extends Matchers
  with ScalaFutures with Eventually {

  override implicit def patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(250, Millis))
}
