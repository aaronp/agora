package jabroni.ui

import jabroni.api.exchange.{QueuedJobs, SubmitJob, WorkSubscription}
import jabroni.api.json.JMatcher
import org.scalajs._
import org.scalajs.dom._

import scalatags.JsDom.all._
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js.annotation.JSExportTopLevel

/**
  * http://www.lihaoyi.com/hands-on-scala-js/#Hands-onScala.js
  * http://www.scala-js.org/tutorial/basic/
  * https://ochrons.github.io/scalajs-spa-tutorial/en/getting-started.html
  */
case class SubmitJobView(services: Services) {

  def exampleSubscription: String = {
    import io.circe.syntax._
    WorkSubscription().asJson.spaces4
  }

  val jsonArea = {
    val ta = textarea(id := "subscription", rows := 30, cols := 70).render
    ta.textContent = exampleSubscription
    ta
  }

  def render(container: html.Div) = {
    container.innerHTML = ""
    container.appendChild(
      div(
        jsonArea
      ).render
    )
  }
}

