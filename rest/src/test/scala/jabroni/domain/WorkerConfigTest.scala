package jabroni.domain

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.optics.JsonPath
import jabroni.api.json.JMatcher
import jabroni.rest.ServerConfig
import jabroni.rest.worker.WorkerConfig
import org.scalatest.{Matchers, WordSpec}

class WorkerConfigTest extends WordSpec with Matchers {
  "WorkerConfig.subscription" should {
    "create a subscription from the default config" in {
      val default = WorkerConfig()
      val Right(sub) = default.subscription
      sub.jobMatcher shouldBe JMatcher.matchAll
      sub.submissionMatcher shouldBe JMatcher.matchAll
    }
    "use the given details" in {

      val default = asConf(
        """ details : {
          |    foo : {
          |      bar : 123
          |    }
          |    topic : meh
          |}""".stripMargin)
      val Right(details) = default.workerDetails

      JsonPath.root.foo.bar.int.getOption(details.aboutMe) shouldBe Option(123)
      JsonPath.root.topic.string.getOption(details.aboutMe) shouldBe Option("meh")
    }
    "create a subscription from the config" in {


      val default = asConf(
        """ jobMatcher : {
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
      val Right(sub) = default.subscription
      sub.jobMatcher shouldBe JMatcher.matchAll.and(JMatcher.matchAll)
      sub.submissionMatcher shouldBe JMatcher.matchAll.or(JMatcher.matchAll)
    }
  }


  def asConf(str: String): WorkerConfig = {
    val c: Config = ConfigFactory.parseString(str).withFallback(WorkerConfig.defaultConfig())
    new WorkerConfig(ServerConfig(c))
  }

}

