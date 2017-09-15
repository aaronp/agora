package agora.exec

import agora.api.BaseSpec
import agora.api.exchange.{Exchange, JobPredicate}
import agora.api.worker.HostLocation
import agora.exec.client.RemoteRunner
import agora.exec.model.{ExecuteProcess, RunProcess}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Properties

class ExecConfigTest extends BaseSpec {

  "ExecConfig()" should {
    "resolve" in {
      ExecConfig().subscription.details.runUser shouldBe Properties.userName
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

  "ExecConfig.runSubscription" should {
    "match RunProcessAndSave jobs with a subscription ID" in {

      val sub1 = ExecConfig().runSubscription.withSubscriptionKey("123")
      val sub2 = ExecConfig().runSubscription.withSubscriptionKey("456")
      val job1 = RemoteRunner.execAsJob(ExecuteProcess(List("hello"), "somedir"), Option("123"))
      val job2 = RemoteRunner.execAsJob(ExecuteProcess(List("hello"), "somedir"), Option("456"))

      JobPredicate().matches(job1, sub1) shouldBe true
      JobPredicate().matches(job1, sub2) shouldBe false
      JobPredicate().matches(job2, sub1) shouldBe false
      JobPredicate().matches(job2, sub2) shouldBe true

      val differentJob = RemoteRunner.execAsJob(RunProcess("hello"), Option("456"))
      JobPredicate().matches(differentJob, sub1) shouldBe false
      JobPredicate().matches(differentJob, sub2) shouldBe false
    }
    "match RunProcessAndSave jobs without a subscription ID" in {
      val sub = ExecConfig().runSubscription.withSubscriptionKey("foo")
      val job = RemoteRunner.execAsJob(ExecuteProcess(List("hello"), "somedir"), None)
      JobPredicate().matches(job, sub) shouldBe true
    }
  }
  "ExecConfig.execSubscription" should {
    "match RunProcess jobs with a subscription ID" in {
      val sub1 = ExecConfig().streamingSubscription.withSubscriptionKey("123")
      val sub2 = ExecConfig().streamingSubscription.withSubscriptionKey("456")
      val job1 = RemoteRunner.execAsJob(RunProcess("hello"), Option("123"))
      val job2 = RemoteRunner.execAsJob(RunProcess("hello"), Option("456"))
      JobPredicate().matches(job1, sub1) shouldBe true
      JobPredicate().matches(job1, sub2) shouldBe false
      JobPredicate().matches(job2, sub1) shouldBe false
      JobPredicate().matches(job2, sub2) shouldBe true
    }
    "match RunProcess jobs without a subscription ID" in {
      val sub1 = ExecConfig().streamingSubscription.withSubscriptionKey("123")
      val sub2 = ExecConfig().streamingSubscription.withSubscriptionKey("456")
      val job1 = RemoteRunner.execAsJob(RunProcess("hello"), None)
      val job2 = RemoteRunner.execAsJob(RunProcess("hello"), None)
      JobPredicate().matches(job1, sub1) shouldBe true
      JobPredicate().matches(job1, sub2) shouldBe true
      JobPredicate().matches(job2, sub1) shouldBe true
      JobPredicate().matches(job2, sub2) shouldBe true
    }
  }

  "ExecConfig.subscriptionGroup" should {
    "make the subscriptions listed in the subscription group" in {
      val ec = ExecConfig()
      import ec.serverImplicits._
      val exchange = Exchange.instance()
      val ids      = ec.execSubscriptions.createSubscriptions(exchange).futureValue

      ids.size shouldBe 2
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

      ec.uploads.dir("xyz").map(_.toAbsolutePath) shouldBe Option("uploads/xyz".asPath.toAbsolutePath)
      ec.logs.dir("abc").map(_.toAbsolutePath) shouldBe Option("logs/abc".asPath.toAbsolutePath)
    }
  }
}
