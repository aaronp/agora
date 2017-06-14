package agora.rest

import com.typesafe.config.ConfigFactory
import agora.rest.worker.WorkerConfig
import org.scalatest.{Matchers, WordSpec}

class RestPackageTest extends WordSpec with Matchers {

  "configForArgs" should {
    "evaluate values from the command line which are referenced from the config file" in {

      val fallback = ConfigFactory.parseString("""
          |foo=defaultValue
          |some.other.value=${foo}
        """.stripMargin)
      val conf     = configForArgs(Array("foo=bar"), fallback).resolve

      conf.getString("some.other.value") shouldBe "bar"
    }
  }
}
