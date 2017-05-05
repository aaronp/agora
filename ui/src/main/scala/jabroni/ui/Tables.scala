package jabroni.ui

import jabroni.api.exchange.SubmitJob
import org.scalajs.dom.html.Table

import scalatags.JsDom.all._


object Tables {
  def asRow(job: SubmitJob) = {
    val jobIdStr: String = job.jobId.getOrElse("N/A")

    tr(
      td(jobIdStr),
      td(job.submissionDetails.submittedBy),
      td(job.submissionDetails.selection.toString)
    )
  }

  def render(jobs: List[SubmitJob]): Table = {
    val header = thead(tr(
      th("id"),
      th("user"),
      th("selection mode")
    ))
    val tableBody = tbody(jobs.map(asRow): _*)

    table(header, tableBody).render
  }

}
