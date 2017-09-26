package agora.exec

import agora.BaseSpec
import agora.api.exchange.{Exchange, JobPredicate}
import agora.api.worker.HostLocation
import agora.exec.client.RemoteRunner
import agora.exec.model.RunProcess
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Properties

class ExecConfigTest extends BaseSpec {

  "ExecConfig()" should {
    "resolve" in {
      val conf = ExecConfig()
      conf.subscription.details.runUser shouldBe Properties.userName
      conf.eventMonitorConfig.enabled shouldBe true
    }
    "ExecConfig(strings...)" should {
      "resolve local config strings" in {
        ExecConfig("subscription.details.runUser=foo").subscription.details.runUser shouldBe "foo"
      }
      "resolve client host and port values from the server host and port" in {
        val conf = ExecConfig("port=6666", "exchange.port=6666", "host=meh")
        conf.host shouldBe "meh"
        conf.port shouldBe 6666
        conf.exchangeConfig.port shouldBe 6666

        conf.clientConfig.host shouldBe "meh"
        conf.clientConfig.port shouldBe 6666
        conf.exchangeConfig.clientConfig.port shouldBe 6666

        val actual = conf.subscription.details.location
        actual shouldBe HostLocation("meh", 6666)
      }
    }
  }

  "ExecConfig.subscriptionGroup" should {
    "make the subscriptions listed in the subscription group" in {
      val ec = ExecConfig()
      import ec.serverImplicits._
      val exchange = Exchange.instance()
      val ids      = ec.execSubscriptions.createSubscriptions(exchange).futureValue

      ids.size shouldBe 1
    }
  }
  "ExecConfig.defaultConfig" should {
    "contain uploadTimeout" in {

      val ec = ExecConfig().withOverrides(ConfigFactory.parseString("""workingDirectory.dir=wd
          |workingDirectory.appendJobId = false
          |logs.dir=logs
          |uploads.dir=uploads
        """.stripMargin))

      ec.uploadTimeout shouldBe 10.seconds

      ec.uploads.pathOpt.map(_.resolve("xyz").toAbsolutePath) shouldBe Option("uploads/xyz".asPath.toAbsolutePath)
    }
  }
}
