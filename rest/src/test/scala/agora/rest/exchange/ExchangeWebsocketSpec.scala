package agora.rest.exchange

import agora.api.Implicits._
import agora.api.exchange.observer.{OnJobSubmitted, OnStateOfTheWorld, TestObserver}
import agora.api.exchange.{SubmitJob, SubmitJobResponse}
import agora.rest.integration.BaseIntegrationTest
import org.scalatest.concurrent.Eventually

import scala.collection.immutable

trait ExchangeWebsocketSpec extends Eventually {
  self: BaseIntegrationTest =>

  implicit def akkaImplicits = exchangeConfig.serverImplicits

  "Exchange observers over a web socket" should {
    "be able to observe state-of-the-world messages" in {
      val obs1 = new TestObserver
      val obs2 = new TestObserver
      exchangeConfig.connectObserver(obs1).futureValue

      // verify we get an empty state of the world for this new exchange
      eventually {
        obs1.stateOfTheWorld.map(_.stateOfTheWorld.jobs) shouldBe Some(Nil)
        obs1.stateOfTheWorld.map(_.stateOfTheWorld.subscriptions) shouldBe Some(Nil)
      }

      // trigger a new job -- our observer should see it
      val firstJob                 = "first job".asJob.withAwaitMatch(false).withId("firstJobId")
      val SubmitJobResponse(jobId) = exchangeClient.submit(firstJob).futureValue
      jobId shouldBe firstJob.jobId.get

      // verify only out connected observer sees it
      eventually {
        obs1.lastSubmitted.map(_.jobSubmitted) shouldBe Some(firstJob)
        obs2.lastSubmitted shouldBe (empty)
      }

      // now connect a second observer and trigger another event
      exchangeConfig.connectObserver(obs2).futureValue

      // verify we see the existing job in the state-of-the-world
      eventually {
        obs2.stateOfTheWorld.map(_.stateOfTheWorld.jobs) shouldBe Some(List(firstJob))
        obs2.stateOfTheWorld.map(_.stateOfTheWorld.subscriptions) shouldBe Some(Nil)
      }

      // trigger a new job -- our observer should see it
      val anotherJob                = "another job".asJob.withAwaitMatch(false).withId("anotherJobId")
      val SubmitJobResponse(jobId2) = exchangeClient.submit(anotherJob).futureValue
      jobId2 shouldBe anotherJob.jobId.get

      eventually {
        obs1.lastSubmitted.map(_.jobSubmitted) shouldBe Some(anotherJob)
        obs2.lastSubmitted.map(_.jobSubmitted) shouldBe Some(anotherJob)
      }

    }
    "be able to observe job submitted events" in {
      val obs          = new TestObserver
      val subscription = exchangeConfig.connectObserver(obs)(exchangeConfig.serverImplicits).futureValue

      // trigger some jobs to observe -- our observer should see it
      val expected: immutable.Seq[SubmitJob] = (0 to 3).map { i =>
        val expected                 = s"testing-$i".asJob.withAwaitMatch(false).withId(s"jobId-$i")
        val SubmitJobResponse(jobId) = exchangeClient.submit(expected).futureValue
        jobId shouldBe expected.jobId.get
        expected
      }

      // our observations are async, separate from the submit
      eventually {
        val submitted: List[SubmitJob] = obs.eventsInTheOrderTheyWereReceived.collect {
          case submitted: OnJobSubmitted => submitted.jobSubmitted
        }
        submitted should contain theSameElementsInOrderAs (expected)
      }

      val all = obs.eventsInTheOrderTheyWereReceived

      all.map(_.getClass) should contain theSameElementsAs (classOf[OnStateOfTheWorld] +: expected.map(_ => classOf[OnJobSubmitted]))
    }
    "not observe events after the subscription is cancelled" in {
      val obs          = new TestObserver
      val subscription = exchangeConfig.connectObserver(obs).futureValue

      // trigger a new job -- our observer should see it
      val firstJob                 = "first job".asJob.withAwaitMatch(false).withId("firstJobId")
      val SubmitJobResponse(jobId) = exchangeClient.submit(firstJob).futureValue
      jobId shouldBe firstJob.jobId.get

      eventually {
        obs.lastSubmitted.map(_.jobSubmitted) shouldBe Some(firstJob)
      }

      def listServerSideObservers = exchangeService.service.exchange.observer.observerList

      // check the server-side state to ensure we have our single websocket observer
      val beforeObservers = listServerSideObservers
      beforeObservers.size shouldBe 1

      // call the method under test - cancel the subscription and then trigger another event
      subscription.cancel()

      eventually {
        obs.lastSubmitted.map(_.jobSubmitted) shouldBe Some(firstJob)
      }

      // the async call should eventually remove it
      eventually {
        listServerSideObservers.size shouldBe beforeObservers.size - 1
      }

      val secondJob                 = "second job".asJob.withAwaitMatch(false).withId("secondJobId")
      val SubmitJobResponse(jobId2) = exchangeClient.submit(secondJob).futureValue
      jobId2 shouldBe secondJob.jobId.get

      withClue("We cancelled the observer so shouldn't have seen the second job event") {
        eventually {
          obs.lastSubmitted.map(_.jobSubmitted) shouldBe Some(firstJob)
        }
      }
    }
  }

}
