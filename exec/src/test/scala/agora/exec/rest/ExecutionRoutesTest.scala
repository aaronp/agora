package agora.exec.rest

import agora.api.exchange.Exchange
import agora.exec.ExecConfig
import agora.exec.run.ExecutionClient
import agora.rest.BaseRoutesSpec
import akka.http.scaladsl.model.ContentTypes
import akka.stream.scaladsl.Source
import akka.util.ByteString

class ExecutionRoutesTest extends BaseRoutesSpec {

  "ExecutionRoutes.execSubscription" should {
    "match "

    val execSubscription = ExecutionRoutes.execSubscription()

    val uploadSubscription = ExecutionRoutes.uploadSubscription()
  }
  "ExecutionRoutes" should {
    "handle uploads to POST /rest/exec/upload" in {

      val execConfig = ExecConfig()
      val exchange   = Exchange.instance()

//      ExchangeClient

      val execRoutes = new ExecutionRoutes(execConfig, exchange, "executions", "uploads") //.futureValue

      val restClient = new DirectRestClient(execRoutes.execRoutes)

      val workspace                    = "someWorkspace"
      val src: Source[ByteString, Any] = Source.single(ByteString("hello world"))
      val request                      = ExecutionClient.asRequest(workspace, "someFile.txt", src, ContentTypes.`text/plain(UTF-8)`)

      request ~> execRoutes.execRoutes ~> check {
        responseAs[List[String]]
      }
    }
  }

}
