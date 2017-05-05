package jabroni.rest.test

import com.typesafe.config.ConfigFactory
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.api.exchange.SubmitJob
import jabroni.rest.{ExchangeMain, ServerConfig, WorkerMain}
import org.scalatest.Matchers

class ExchangeSteps extends ScalaDsl with EN with Matchers with TestData {

  var state = ExchangeTestState()

  Given("""^I start an exchange with command line (.*)$""") { (commandLine: String) =>
    val config = ExchangeMain.configForArgs(commandLine.split("\\s+", -1))
    state = state.startExchangeServer(config)
  }

  Given("""^I start a worker with command line (.*)$""") { (commandLine: String) =>
    val config = WorkerMain.configForArgs(commandLine.split("\\s+", -1))
    state = state.startWorker(config)
  }
  Given("""^I start a worker with config (.*)$""") { (configString: String) =>
    val config = {
      val conf = ConfigFactory.parseString(configString).withFallback(WorkerMain.defaultConfig)
      ServerConfig(conf)
    }
    state = state.startWorker(config)
  }

  When("""^worker (.*) creates a subscription to jobs matching jobs on (.*)$""") { (name: String, configString: String) =>
    ???
  }
  When("""^matching details on (.*)$""") { (name: String, configString: String) =>
    ???
  }

  When("""^I submit a job$""") { (submitJson: String) =>
    import io.circe.parser._
    val Right(job) = decode[SubmitJob](submitJson)
    state = state.submitJob(job)
  }

  Then("""^the job queue should be$""") { (expectedTable: DataTable) =>
    val rows: List[Map[String, String]] = expectedTable.toMap
    val (newState, jobs: List[SubmitJob]) = state.jobQueue
    state = newState
    val actualRows = jobs.map { job =>
      Map(
        "jobId" -> job.jobId.getOrElse("N/A"),
        "submissionUser" -> job.submissionDetails.submittedBy)
    }
    actualRows should contain theSameElementsAs rows
  }

  Then("""^the worker queue should be empty$""") { () =>
    val (newState, subscriptions) = state.subscriptionQueue
    state = newState
    subscriptions shouldBe List()
  }


  After { _ =>
    state = state.close()
  }

}
