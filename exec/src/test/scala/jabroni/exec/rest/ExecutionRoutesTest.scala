package jabroni.exec.rest

import akka.http.scaladsl.model.Multipart
import akka.stream.scaladsl.Source
import akka.util.ByteString
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{Exchange, JobPredicate, MatchObserver}
import jabroni.exec.model.{RunProcess, Upload}
import jabroni.exec.run.RemoteRunner
import jabroni.exec.{ExecConfig, ExecutionHandler}
import jabroni.rest.BaseRoutesSpec
import jabroni.rest.client.RestClient
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.multipart.MultipartBuilder
import jabroni.rest.worker.{WorkerClient, WorkerRoutes}

class ExecutionRoutesTest extends BaseRoutesSpec {


  class Setup {

    val conf = ExecConfig()
    val handler = ExecutionHandler(conf)

    val exchange = Exchange(MatchObserver())(JobPredicate())
    // create a worker to subscribe to the exchange
    val workerRoutes: WorkerRoutes = conf.newWorkerRoutes(exchange)
    workerRoutes.addMultipartHandler(handler.onExecute)(conf.subscription, 1)

    val executionRoutes = ExecutionRoutes(conf)

    val routes = executionRoutes.routes(workerRoutes, None)

    def exchangeClient(restClient: RestClient) = {
      ExchangeClient(restClient) { workerLocation =>
        println("ignoring " + workerLocation)
        WorkerClient(restClient)
      }
    }

    def runner(rest: RestClient) = RemoteRunner(exchangeClient(rest), 1024, true, true)
  }

  "GET /rest/exec/metadata?foo=bar" should {
    "return jobs which have matching metadata" in {

      val setup = new Setup
//
//      val text = "echo I'm being run from a script"
//      val bytes = ByteString(text)
//      val data = Source.single(bytes)
//      val form: MultipartBuilder = MultipartBuilder().
//        text("command", "script.sh").
//        fromSource("script.sh", bytes.size, data, fileName = "script.sh")

      val client = DirectRestClient(setup.routes)

      val runner = setup.runner(client)

      val output = runner.run(RunProcess("script.sh").addMetadata("foo" -> "bar"), List(Upload.forText("script.sh", "echo I am a script"))).futureValue
      output.mkString("") shouldBe "I am a script"


      //
      //      val response = form.formData.flatMap { (strict: Multipart.FormData.Strict) =>
      //        val request = WorkerClient.multipartRequest("handler", MatchDetails.empty, strict)
      //        client.send(request)
      //      }.futureValue
      //


    }
  }
  "GET /rest/exec/metadata?foo=~bar" ignore {
    "return jobs which have metadata which contains bar for foo" in {

    }
  }

}
