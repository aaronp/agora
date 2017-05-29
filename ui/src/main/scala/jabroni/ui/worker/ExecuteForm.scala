package jabroni.ui.worker

import jabroni.api.JobId
import jabroni.ui.Services
import org.scalajs.dom.html.Button
import org.scalajs.dom.raw.{FormData, _}
import org.scalajs.dom.{Event, console, html}

import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._

object ExecuteForm {


  def websocket(): WebSocket = {
    val url = Services.baseWebsocketUri + s"/rest/exec/run"

    new WebSocket(url)
  }

  @JSExportTopLevel("ameliorateForm")
  def ameliorateForm(form: html.Form,
                     formDiv: html.Div) = {
    val submitBtn: Button = button(`class` := "mdc-button mdc-button--raised", `type` := "submit")("Run").render
    submitBtn.onclick = (evt: Event) => {
      evt.cancelBubble
      evt.preventDefault()
      val formData = new FormData(form)
      val idFuture = AjaxExecute.submitJob(formData)
      idFuture.foreach { jobId =>
        //        AjaxExecute.streamResults(jobId)

        val socket = websocket()
        socket.onopen = { (event: Event) => {
          console.info(s"onopen ${event}")
          console.info(s"sending ${jobId}")
          socket.send(jobId)
        }
        }
        socket.onclose = { (event: Event) => {
          console.info(s"onclose${event}")
        }
        }

        socket.onerror = { (event: ErrorEvent) => {
          console.info(s"onError:${event}")
        }
        }
        socket.onmessage = { (msg: MessageEvent) => {
          console.info(s"${msg.data}")
          append(msg.data.toString)

        }
        }
      }

    }

    def append(text: String) = {
      formDiv.insertBefore(p(text).render, formDiv.firstChild)
    }

    formDiv.appendChild(submitBtn)
  }

  @JSExportTopLevel("onRun")
  def onRun(formId: String): Unit = {
    val x = org.scalajs.dom.document.getElementById(formId)
    Services.Alert(s"onRun $x")
  }

}
