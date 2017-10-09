package agora.api.io

import _root_.io.circe.optics.JsonPath
import agora.BaseSpec
import agora.api.json.{And, JPredicate, Or}
import agora.rest.worker.WorkerConfig
import com.typesafe.config.ConfigFactory

class WorkerConfigTest extends BaseSpec {
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
      default.subscription.jobCriteria shouldBe JPredicate.matchAll
      default.subscription.submissionCriteria shouldBe JPredicate.matchAll
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
          |  jobCriteria : {
          |    and : [ "match-all", "match-none" ]
          |  }
          |  submissionCriteria : {
          |    or : [ "match-none", "match-all" ]
          |  }
          |}""".stripMargin)
      val sub     = default.subscription
      import JPredicate._
      sub.jobCriteria shouldBe And(List(matchAll, matchNone))
      sub.submissionCriteria shouldBe Or(List(matchNone, matchAll))
    }
  }

  def asConf(str: String): WorkerConfig = {
    val c = WorkerConfig(ConfigFactory.parseString(str))
    c.withFallback(WorkerConfig().config)
  }

}
