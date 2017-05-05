package jabroni.ui

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js.annotation.JSExportTopLevel

object MainView {


  @JSExportTopLevel("loadView")
  def loadView(
                jobContainer: html.Div,
                newJobContainer: html.Div,
                subscriptions: html.Div
              ) = {
    val svcs = Services()
    JobsView.refresh(svcs, jobContainer)
    SubmitJobView(svcs).render(newJobContainer)

  }
}
