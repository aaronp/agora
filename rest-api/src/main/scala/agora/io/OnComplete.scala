package agora.io

import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.util.Try

object OnComplete {
  def onDownstreamComplete[A, B](f: () => Unit) = {
    val onc: OnComplete[A, B] = new OnComplete(_ => {}, f)
    Flow.fromGraph(onc)
  }

  def onUpstreamComplete[A, B](f: Option[Throwable] => Unit) = {
    val onc: OnComplete[A, B] = new OnComplete(f, () => {})
    Flow.fromGraph(onc)
  }
}

class OnComplete[In, Out](upstreamCallback: Option[Throwable] => Unit, downstreamCallback: () => Unit)
    extends GraphStage[FlowShape[In, Out]] {
  val in: Inlet[In]    = Inlet[In]("OnComplete.in")
  val out: Outlet[Out] = Outlet[Out]("OnComplete.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      // This requests one element at the Sink startup.
      override def preStart(): Unit = pull(in)

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            val elm = grab(in)
            push(out.as[Any], elm)
          }

          override def onUpstreamFinish() = {
            Try(upstreamCallback(None))
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable) = {
            Try(upstreamCallback(Option(ex)))
            super.onUpstreamFailure(ex)
          }
        }
      )
      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            if (!hasBeenPulled(in)) {
              pull(in)
            }
          }

          override def onDownstreamFinish = {
            Try(downstreamCallback())
            super.onDownstreamFinish()
          }
        }
      )

    }
}
