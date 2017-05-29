package jabroni.ui.worker

import jabroni.ui.Services
import org.scalajs.dom.html.Button
import org.scalajs.dom.raw.{FormData, _}
import org.scalajs.dom.{Event, html}

import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.all._

class ExecuteForm(socket: WebSocket, outputTarget: HTMLElement) {

  socket.onopen = { (event: Event) ⇒
    append("stdout: ")
  }
  socket.onerror = { (event: ErrorEvent) ⇒
    append(s"Failed: code: ${event.colno}")
    close()
  }
  socket.onmessage = { (event: MessageEvent) ⇒
    val text = event.data.toString
    append(text)
  }
  socket.onclose = { (event: Event) ⇒
    close()
  }

  def close() = {
    outputTarget.disabled = true
  }

  def append(text: String) = {
    outputTarget.insertBefore(p(text).render, outputTarget.firstChild)
  }
}

object ExecuteForm {


  def websocket(): WebSocket = new WebSocket(Services.baseWebsocketUri + "/rest/exec/run")


  @JSExportTopLevel("ameliorateForm")
  def ameliorateForm(form: html.Form,
                     formDiv: html.Div
                    ) = {

    //<button type="button" class="mdc-button mdc-button--raised" id="execute-button">Run</button>

    //attr("suggestion") := "clickme"
    val submitBtn: Button = button(`class` := "mdc-button mdc-button--raised", `type` := "submit")("Run").render
    submitBtn.onclick = (evt: Event) => {
      Services.Alert("submit!" + evt)

      evt.cancelBubble
      evt.preventDefault()
      AjaxExecute.execute(new FormData(form))
      //form.submit()
    }

    formDiv.appendChild(submitBtn)

  }

  @JSExportTopLevel("onRun")
  def onRun(formId: String): Unit = {
    val x = org.scalajs.dom.document.getElementById(formId)
    Services.Alert(s"onRun $x")
  }

}
