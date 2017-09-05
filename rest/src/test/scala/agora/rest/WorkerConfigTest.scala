package agora.api.io

import com.typesafe.config.{Config, ConfigFactory}
import agora.api.json.JMatcher
import agora.rest.worker.WorkerConfig
import _root_.io.circe.optics.JsonPath
import org.scalatest.{Matchers, WordSpec}

class WorkerConfigTest extends WordSpec with Matchers {
  "WorkerConfig(args)" should {
    "produce a worker config from user args" in {
      val wc: WorkerConfig = WorkerConfig("subscription.details.path=foo", "port=1122", "exchange.port=567")
      wc.location.port shouldBe 1122
      wc.exchangeConfig.location.port shouldBe 567

      wc.subscription.details shouldBe wc.subscription.details

      wc.subscription.details.location.port shouldBe 1122
      wc.subscription.details.path shouldBe "foo"
    }
  }
  "WorkerConfig.subscription" should {
    "create a subscription from the default config" in {
      val default = WorkerConfig()
      default.subscription.jobMatcher shouldBe JMatcher.matchAll
      default.subscription.submissionMatcher shouldBe JMatcher.matchAll
    }
    "use the given details" in {

      val default = asConf("""subscription.details : {
          |    foo : {
          |      bar : 123
          |    }
          |    topic : meh
          |}""".stripMargin)
      val details = default.subscription.details

      JsonPath.root.foo.bar.int.getOption(details.aboutMe) shouldBe Option(123)
      JsonPath.root.topic.string.getOption(details.aboutMe) shouldBe Option("meh")
    }
    "create a subscription from the config" in {
      val default = asConf("""subscription {
          |  jobMatcher : {
          |    and : [ "match-all", "match-none" ]
          |  }
          |  submissionMatcher : {
          |    or : [ "match-none", "match-all" ]
          |  }
          |}""".stripMargin)
      val sub     = default.subscription
      sub.jobMatcher shouldBe JMatcher.matchAll.and(JMatcher.matchNone)
      sub.submissionMatcher shouldBe JMatcher.matchNone.or(JMatcher.matchAll)
    }
  }

  def asConf(str: String): WorkerConfig = {
    val c = WorkerConfig(ConfigFactory.parseString(str))
    c.withFallback(WorkerConfig().config)
  }

}
