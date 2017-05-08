package jabroni.rest

import com.typesafe.config.ConfigFactory
import jabroni.rest.worker.WorkerConfig
import org.scalatest.{Matchers, WordSpec}

class BootTest extends WordSpec with Matchers {

  "Boot.configForArgs" should {
    "evaluate values from the command line which are referenced from the config file" in {

      val fallback = ConfigFactory.parseString(
        """
          |foo=defaultValue
          |some.other.value=${foo}
        """.stripMargin).withFallback(WorkerConfig.defaultConfig())
      val conf = Boot.configForArgs(Array("foo=bar"), fallback)
      conf.getString("some.other.value") shouldBe "bar"
    }
  }
}