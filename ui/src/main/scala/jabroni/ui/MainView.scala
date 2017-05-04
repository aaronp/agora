package jabroni.ui

import org.scalajs.dom.html

import org.scalajs.dom
import dom.{document, html}

import scalatags.JsDom.all._
import scala.scalajs.concurrent.JSExecutionContext.queue
import org.scalajs.dom
import dom.html
import dom.ext.Ajax
import scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel


import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.all.{`type`, div, h1, input, placeholder}

object MainView {


  @JSExportTopLevel("loadView")
  def loadView(jobContainer: html.Div) = {
    val svcs = Services()
    JobView(svcs.exchange)

    val box = input(
      `type` := "textarea",
      placeholder := "Type here!"
    ).render

    container.appendChild(
      div(
        h1("File Search"),
        box
      ).render
    )
  }
}
