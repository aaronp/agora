package jabroni.ui

import org.scalajs.dom
import dom.{document, html}

import scalatags.JsDom.all._
import scala.scalajs.concurrent.JSExecutionContext.queue
import org.scalajs.dom
import dom.html
import dom.ext.Ajax
import jabroni.api.exchange.{Exchange, ObserverResponse, QueuedJobs, SubmitJob}
import jabroni.api.json.JMatcher

import scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel


/**
  * http://www.lihaoyi.com/hands-on-scala-js/#Hands-onScala.js
  * http://www.scala-js.org/tutorial/basic/
  * https://ochrons.github.io/scalajs-spa-tutorial/en/getting-started.html
  */
case class JobsView(services: Services) {


  def render(jobContainer: html.Div, jobs: List[SubmitJob]): Unit = {

  }

  def refresh(jobContainer: html.Div): Unit = {
    val res = services.exchange.listJobs(QueuedJobs(JMatcher.matchAll, JMatcher.matchAll))
    res.onSuccess {
      case jobs => render(jobContainer, jobs.jobs)
    }
    res.onFailure {
      case err => services.onError(err)
    }
  }

  def appendPar(targetNode: dom.Node, text: String): Unit = {

    val parNode = document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }


}
