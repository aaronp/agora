package agora.rest.test

import agora.api.exchange.SubmitJob
import agora.rest.exchange.{ExchangeConfig, ExchangeServerConfig}
import agora.rest.worker.{WorkContext, WorkerConfig}
import com.typesafe.config.ConfigFactory
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import org.scalatest.Matchers

class ExchangeSteps extends ScalaDsl with EN with Matchers with TestData {

  var state = ExchangeTestState()

  object PendingLock

  var pendingRequestsForWorker = Map[String, List[WorkContext[String]]]()

  Given("""^an exchange is started with command line (.*)$""") { (commandLine: String) =>
    val config = ExchangeServerConfig(commandLine.split("\\s+", -1))
    state = state.startExchangeServer(config)
  }
  Given("""^an exchange is started$""") { () =>
    state = state.startExchangeServer(ExchangeServerConfig())
  }
  Given("""^worker (.*) is started$""") { (name: String) =>
    val config = WorkerConfig(s"subscription.details.name=$name")
    state = state.startWorker(name, config)
  }
  Given("""^worker (.*) is started with command line (.*)$""") { (name: String, commandLine: String) =>
    val config = WorkerConfig(s"subscription.details.name=$name" +: commandLine.split("\\s+", -1))
    state = state.startWorker(name, config)
  }
  Given("""^worker (.*) is started with config (.*)$""") { (name: String, configString: String) =>
    val config = {
      val conf = ConfigFactory.parseString(configString).withFallback(WorkerConfig(s"details.name=$name").config)
      WorkerConfig(conf)
    }
    state = state.startWorker(name, config)
  }

  When("""^worker (.*) asks for (\d+) work items using subscription (.*)$""") {
    (name: String, items: Int, subscriptionKey: String) =>
      state = state.take(name, subscriptionKey, items)
  }
  Then("""^the exchange should respond to (.*) with (\d+) previous items pending and (\d+) total items pending$""") {
    (subscriptionKey: String, expectedPreviousPending: Int, expectedTotal: Int) =>
      state = state.verifyTakeAck(subscriptionKey, expectedPreviousPending, expectedTotal)
  }
  When("""^worker (.*) creates work subscription (.*) with$""") {
    (name: String, subscriptionKey: String, subscriptionJson: String) =>
      val worker = state.workersByName(name)

      val conf = WorkerConfig(s"subscription.details.id=$subscriptionKey")
        .withOverrides(ConfigFactory.parseString(subscriptionJson))
      conf.subscription.details.subscriptionKey shouldBe Option(subscriptionKey)

      worker.service.usingSubscription(_ => conf.subscription).withInitialRequest(0).addHandler[String] { ctxt =>
        PendingLock.synchronized {
          val newList: List[WorkContext[String]] = pendingRequestsForWorker.getOrElse(name, Nil)
          pendingRequestsForWorker = pendingRequestsForWorker.updated(name, ctxt :: newList)
        }
      }
  }

  When("""^I submit a job$""") { (submitJson: String) =>
    import io.circe.parser._
    val Right(job) = decode[SubmitJob](submitJson)
    state = state.submitJob(job)
  }

  Then("""^the job queue should be$""") { (expectedTable: DataTable) =>
    val rows: List[Map[String, String]]   = expectedTable.toMap
    val (newState, jobs: List[SubmitJob]) = state.jobQueue
    state = newState
    val actualRows = jobs.map { job =>
      Map("jobId" -> job.jobId.getOrElse("N/A"), "submissionUser" -> job.submissionDetails.submittedBy)
    }
    actualRows should contain theSameElementsAs rows
  }
  Then("""^the worker queue should be$""") { (expectedTable: DataTable) =>
    val rows: List[Map[String, String]] = expectedTable.toMap
    val (newState, subscriptions)       = state.subscriptionQueue
    state = newState
    val actualRows = subscriptions.map { sub =>
      Map("subscriptionKey" -> sub.key, "requested" -> sub.requested.toString)
    }
    actualRows should contain theSameElementsAs rows
  }

  Then("""^the worker queue should be empty$""") { () =>
    val (newState, subscriptions) = state.subscriptionQueue
    state = newState
    subscriptions shouldBe empty
  }
  Then("""^the job queue should be empty$""") { () =>
    val (newState, jobs) = state.jobQueue
    state = newState
    jobs shouldBe empty
  }

  After { _ =>
    state = state.close()
  }

}
