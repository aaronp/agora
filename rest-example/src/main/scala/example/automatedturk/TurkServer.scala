package example.automatedturk

import agora.rest.RestImplicits._
import agora.rest.worker.WorkerConfig
import agora.rest.worker.WorkerConfig.RunningWorker
import io.circe.generic.auto._

import scala.io.StdIn

object TurkServer {
  def main(a: Array[String]) = {
    val conf = WorkerConfig(a)
    import conf.serverImplicits._
    conf.startWorker().foreach(run)
  }

  def run(worker: RunningWorker) = {
    worker.service.usingSubscription(_.withPath("ask")).addHandler[AskQuestion] { ctxt =>
      ctxt.updateSubscriptionDetails(ctxt.request.uses)
      ctxt.complete {
        val answer = StdIn.readLine(s"${ctxt.request.question} > ")
        answer
      }
    }

  }
}
