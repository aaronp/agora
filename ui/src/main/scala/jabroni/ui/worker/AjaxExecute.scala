package jabroni.ui.worker

import jabroni.ui.{AjaxClient, Services}
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.{Event, ProgressEvent, XMLHttpRequest, console}

import scala.concurrent.Future

object AjaxExecute extends AjaxClient("/rest/exec") {

  def execute(form: org.scalajs.dom.FormData) = {
    Services.Alert("posting " + form)
    val post: Future[XMLHttpRequest] = Ajax.post(baseUrl + "/run", form)
    post.map { resp: XMLHttpRequest =>
      resp.onloadstart = (evt: Any) => {
        console.info("onloadStart: " + evt)
      }
      resp.onload = (evt: Event) => {
        console.info("onload: " + evt)
      }
      resp.onloadend = (evt: ProgressEvent) => {
        console.info("onloadEnv: " + evt)
      }
    }
  }
}
