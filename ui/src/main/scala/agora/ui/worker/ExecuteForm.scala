package agora.ui.worker

import agora.ui.Services
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{FormData, _}
import org.scalajs.dom.{Event, console, html}

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.all._

object ExecuteForm {

  def websocket(): WebSocket = {
    val url = s"${Services.baseWebsocketUri}/rest/exec/run"

    new WebSocket(url)
  }

  def execute(form: html.Form, evt: Event)(onOutput: Boolean => String => Unit) = {
    evt.cancelBubble
    evt.preventDefault()
    val onStdOut = onOutput(true)
    val onStdErr = (line: String) => {
      console.log("ERR: " + line)
      onOutput(false)(line)
    }
    val formData = new FormData(form)
    val idFuture = AjaxExecute.submitJob(formData)
    idFuture.foreach { jobId =>
      //        AjaxExecute.streamResults(jobId)

      val socket = websocket()
      socket.onopen = { (event: Event) =>
        {
          socket.send(jobId)
        }
      }

      var nextOutIsError = false
      socket.onerror = { (msg: ErrorEvent) =>
        {
          Services.Alert(s"onErrror(${msg.message}, lineno is ${msg.lineno} )")
        }
      }

      socket.onmessage = { (msg: MessageEvent) =>
        {
          val line = msg.data.toString
          console.info(s"DATA: ${line}")
          if (nextOutIsError) {

            val errJson = msg.data.toString
            val json    = JSON.parse(errJson)

            val values: js.Array[js.Dynamic] = json.stdErr.asInstanceOf[js.Array[js.Dynamic]]
            if (values.length == 0) {
              onStdErr(s"Error w/ exit code '${json.exitCode}'")
            } else {
              values.map(_.toString).foreach(onStdErr)
            }
          } else {
            nextOutIsError = line == "*** _-={ E R R O R }=-_ ***"
            if (!nextOutIsError) {
              console.log(s"OUT: " + line)
              onStdOut(line)
            }
          }
        }
      }
    }
  }

  @JSExportTopLevel("ameliorateForm")
  def ameliorateForm(form: html.Form, formDiv: html.Div, resultsDiv: html.Div) = {

    def append(target: html.Div, c1ass: String, text: String) = {
      target.insertBefore(div(`class` := c1ass)(text).render, target.firstChild)
    }

    val execText = input(`class` := "input-text", `type` := "text", name := "command", id := "command-id", placeholder := "...", value := "")().render

    def execDiv(textOutput: Div) = {
      div(
        div(`class` := "output-section")(
          h4(execText.value)
        ),
        textOutput
      ).render
    }

    execText.onkeypress = (keyPress: KeyboardEvent) => {
      if (keyPress.charCode == 13) {
        val textOutput: Div = div(`class` := "output-text")("").render

        resultsDiv.insertBefore(execDiv(textOutput), resultsDiv.firstChild)

        execute(form, keyPress) {
          case true =>
            line =>
              append(textOutput, "output-line", line)
              ()
          case false =>
            line =>
              append(textOutput, "error-line", line)
              ()
        }
        execText.value = ""
      }
    }

    formDiv.appendChild(div(`class` := "form-inputs")(execText).render)
  }

  @JSExportTopLevel("onRun")
  def onRun(formId: String): Unit = {
    val x = org.scalajs.dom.document.getElementById(formId)
    Services.Alert(s"onRun $x")
  }

}
