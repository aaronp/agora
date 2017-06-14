package agora.domain

import com.typesafe.config.{Config, ConfigFactory}
import agora.api.json.JMatcher
import agora.rest.worker.WorkerConfig
import _root_.io.circe.optics.JsonPath
import org.scalatest.{Matchers, WordSpec}

class WorkerConfigTest extends WordSpec with Matchers {
  "WorkerConfig(args)" should {
    "produce a worker config from user args" in {
      val wc: WorkerConfig = WorkerConfig("details.path=foo", "port=1122", "exchange.port=567")
      wc.location.port shouldBe 1122
      wc.exchangeConfig.location.port shouldBe 567

      wc.subscription.details shouldBe wc.workerDetails

      wc.subscription.details.location.port shouldBe 1122
      wc.subscription.details.path shouldBe Option("foo")
    }
  }
  "WorkerConfig.subscription" should {
    "create a subscription from the default config" in {
      val default = WorkerConfig()
      default.subscription.jobMatcher shouldBe JMatcher.matchAll
      default.subscription.submissionMatcher shouldBe JMatcher.matchAll
    }
    "use the given details" in {

      val default = asConf(""" details : {
          |    foo : {
          |      bar : 123
          |    }
          |    topic : meh
          |}""".stripMargin)
      val details = default.workerDetails

      JsonPath.root.foo.bar.int.getOption(details.aboutMe) shouldBe Option(123)
      JsonPath.root.topic.string.getOption(details.aboutMe) shouldBe Option("meh")
    }
    "create a subscription from the config" in {
      val default = asConf(""" jobMatcher : {
          |      and : {
          |       lhs : match-all
          |       rhs : match-all
          |      }
          |}
          |submissionMatcher : {
          |    or : {
          |       lhs : match-all
          |       rhs : match-all
          |      }
          |}
          |""".stripMargin)
      val sub     = default.subscription
      sub.jobMatcher shouldBe JMatcher.matchAll.and(JMatcher.matchAll)
      sub.submissionMatcher shouldBe JMatcher.matchAll.or(JMatcher.matchAll)
    }
  }

  def asConf(str: String): WorkerConfig = {
    val c = WorkerConfig(ConfigFactory.parseString(str))
    c.withFallback(WorkerConfig().config)
  }

}
