package agora.exec.ws

import agora.exec.ExecConfig
import agora.exec.log.StreamLogger
import agora.exec.model.{RunProcess, StreamingResult, StreamingSettings}
import agora.exec.client.ProcessRunner
import akka.NotUsed
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Execute w/ WebSockets
  *
  * see https://github.com/akka/akka-http/blob/v10.0.7/docs/src/test/scala/docs/http/scaladsl/server/WebSocketExampleSpec.scala
  *
  */
object ExecuteOverWS extends LazyLogging {

  import akka.http.scaladsl.model.ws.{Message, TextMessage}
  import akka.stream.scaladsl.Flow

  def apply(execConfig: ExecConfig)(implicit mat: Materializer): Flow[Message, TextMessage, NotUsed] = {

    import mat._

    Flow[Message]
      .mapConcat {
        case inputMessage: TextMessage =>
          logger.info(s"On text message $inputMessage")

          // create a process logger which will stream the output
          val output = StreamLogger()

          val fut = for {

            /**
              * The first message on open is just the raw job id:
              *
              * See ExecuteForm:
              * {{{
              * val socket = websocket()
              * socket.onopen = { (event: Event) =>
              *   socket.send(jobId)
              * }}}
              */
            jobId <- inputMessage.textStream.runWith(Sink.head)

            /**
              * load up our saved job/uploads based on the job ID
              */
            runProcess: RunProcess <- Future.successful[RunProcess](???)
            runner                 <- Future.successful[ProcessRunner](???) //(workDir = execConfig.uploadsDir.resolve(jobId)).add(output)

            StreamingResult(output) <- runner.run(runProcess)
          } yield {
            output.size
          }

          val outputIterator = output.iterator

          val iter = new Iterator[Either[String, String]] {
            var done = false

            override def hasNext: Boolean = !done && Try(outputIterator.hasNext).getOrElse(false)

            override def next() = {
              Try(outputIterator.next) match {
                case Success(line) if line == StreamingSettings.DefaultErrorMarker =>
                  done = true
                  val json = outputIterator.mkString("\n")
                  Left(json)
                case Success(line) => Right(line)
                case Failure(e) =>
                  val remaining = Try(outputIterator.size)
                  Left(e.getMessage)
              }
            }
          }

          iter.flatMap {
            case Right(line) => TextMessage(line) :: Nil
            case Left("")    => Nil
            case Left(json) =>
              TextMessage(StreamingSettings.DefaultErrorMarker) :: TextMessage(json) :: Nil
          }.toStream

        case bm: BinaryMessage =>
          logger.info("Ignoring binary message")
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
  }

}
