package jabroni.ui

import jabroni.api.exchange.SubmitJob
import org.scalajs.dom.html

import scala.scalajs.js.annotation.JSExport

/**
  * http://www.lihaoyi.com/hands-on-scala-js/#Hands-onScala.js
  * http://www.scala-js.org/tutorial/basic/
  * https://ochrons.github.io/scalajs-spa-tutorial/en/getting-started.html
  */
object JobsView {

  def render(jobContainer: html.Div, jobs: List[SubmitJob]): Unit = {
    jobContainer.innerHTML = ""
    jobContainer.appendChild(tableForJobs(jobs).render)
  }

  def tableForJobs(jobs: List[SubmitJob]) = {
    Tables.forJobs(jobs)
  }

  @JSExport()
  def refresh(services: Services, jobContainer: html.Div): Unit = {
//    val res = services.observer.listJobs(QueuedJobs(JMatcher.matchAll, JMatcher.matchAll))
//    //    val res = services.exchange.listJobs(null)
//    res.onSuccess {
//      case jobs => render(jobContainer, jobs.jobs)
//    }
//    res.onFailure {
//      case err => services.onError(err)
//    }
  }
}
