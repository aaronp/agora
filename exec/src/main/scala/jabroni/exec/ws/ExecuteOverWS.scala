package jabroni.exec.ws

import akka.NotUsed
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.LazyLogging
import jabroni.domain.IterableSubscriber
import jabroni.exec.ExecConfig
import jabroni.exec.run.ProcessRunner

import scala.concurrent.{Await, Future}

/**
  * Execute w/ WebSockets
  *
  * see https://github.com/akka/akka-http/blob/v10.0.7/docs/src/test/scala/docs/http/scaladsl/server/WebSocketExampleSpec.scala
  *
  */
object ExecuteOverWS extends LazyLogging {

  import akka.http.scaladsl.model.ws.{Message, TextMessage}
  import akka.stream.scaladsl.{Flow, Source}

  def apply(src: Source[String, Any])(implicit mat: Materializer): Flow[Message, TextMessage, NotUsed] = {

    Flow[Message]
      .mapConcat {
        case tm: TextMessage =>
          tm.textStream.runWith(Sink.ignore)
          TextMessage(src) :: Nil
        case bm: BinaryMessage =>
          logger.info("Ignoring binary message")
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
  }

  def apply(execConfig: ExecConfig)(implicit mat: Materializer): Flow[Message, TextMessage, NotUsed] = {

    import mat._

    Flow[Message]
      .mapConcat {
        case inputMessage: TextMessage =>

          logger.info(s"On text message $inputMessage")

          val fut: Future[Source[String, NotUsed]] = for {
            jobId <- inputMessage.textStream.runWith(Sink.head)
            dao = execConfig.execDao
            (runProcess, uploads) <- dao.get(jobId)
            runner = ProcessRunner(
              dao.uploadDao(jobId),
              workDir = execConfig.workingDirectory.dir(jobId),
              execConfig.newLogger(jobId, None))

          } yield {
            val src = Source.fromFuture(runner.run(runProcess, uploads)).flatMapConcat { iter =>
              Source.fromIterator(() => iter)
            }
            src
          }

          import concurrent.duration._
          val src: Source[String, NotUsed] = Await.result(fut, 10.seconds)
          IterableSubscriber.iterate(src).map { line =>
            TextMessage(line)
          }.toStream

        case bm: BinaryMessage =>
          logger.info("Ignoring binary message")
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
  }

}
