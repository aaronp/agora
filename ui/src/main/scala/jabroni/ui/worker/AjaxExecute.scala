package jabroni.ui.worker

import jabroni.api.JobId
import jabroni.ui.AjaxClient
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.{XMLHttpRequest, console}

import scala.concurrent.Future
import scala.scalajs.js.JSON

object AjaxExecute extends AjaxClient("/rest/exec") {





  /**
    * Submits a job to be saved to be executed later like
    *
    * @param form
    * @return
    */
  def submitJob(form: org.scalajs.dom.FormData): Future[JobId] = {
    Ajax.post(baseUrl + "/submit", form).map {
      resp: XMLHttpRequest =>

        val responseJson = JSON.parse(resp.responseText)
        val jobId = responseJson.jobId

        console.info(s"${
          resp.status
        } (${
          resp.statusText
        }): ${
          resp.responseText
        } ... jobId is $jobId")

        jobId.toString
    }
  }

}
