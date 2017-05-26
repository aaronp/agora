package jabroni.ui.worker

import jabroni.ui.{JobsView, Services}
import org.scalajs.dom.html.Button
import org.scalajs.dom.raw.FormData
import org.scalajs.dom.{Event, html}

import scalatags.JsDom.all._
import scala.scalajs.js.annotation.JSExportTopLevel

object ExecuteForm {


  @JSExportTopLevel("ameliorateForm")
  def ameliorateForm(
                      form: html.Form,
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
