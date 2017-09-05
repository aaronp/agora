package miniraft.state.rest

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.{BiPredicate, Consumer}

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import miniraft._
import miniraft.state._

import agora.api.io.implicits._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class LoggingEndpoint[T: Encoder: Decoder](ourNodeId: NodeId, underlying: RaftEndpoint[T], saveUnder: Path, counter: AtomicInteger, numberOfMessageToKeep: Int)(
    implicit ec: ExecutionContext)
    extends RaftEndpoint[T]
    with StrictLogging {

  override def onVote(vote: RequestVote): Future[RequestVoteResponse] = {

    save("request-vote.txt", vote.asJson.spaces4)

    val future = underlying.onVote(vote)
    future.onComplete {
      case Failure(err) =>
        save("-response-vote-err.txt", "" + err)
      case Success(resp) =>
        save("response-vote.txt", resp.asJson.spaces4)
    }
    future
  }

  override def onAppend(append: AppendEntries[T]): Future[AppendEntriesResponse] = {
    if (append.isHeartbeat) {
      save("request-heartbeat.txt", append.asJson.spaces4, true)
    } else {
      save("request-append.txt", append.asJson.spaces4)
    }

    val future = underlying.onAppend(append)
    future.onComplete {
      case Failure(err)  => save("response-append-er.txt", "" + err)
      case Success(resp) => save("response-append.txt", resp.asJson.spaces4)
    }
    future
  }

  private def save(suffix: String, content: String, trace: Boolean = false) = {

    val msgIdx   = counter.incrementAndGet()
    val fileName = s"${msgIdx}-$suffix"
    val msg      = s"$ourNodeId $fileName : ${content.lines.mkString(" ").replaceAll("\\s+", " ")}"
    if (trace) {
      logger.trace(msg)
    } else {
      logger.debug(msg)
    }

    saveUnder.resolve(fileName).text = content
    val threshold = msgIdx - numberOfMessageToKeep
    LoggingEndpoint.removeFilesWithAnIntegerPrefixPriorToN(saveUnder, threshold)
  }
}

object LoggingEndpoint {

  private val IndexedFile = "(\\d+)-.*".r

  def removeFilesWithAnIntegerPrefixPriorToN(saveUnder: Path, n: Int) = {
    val predicate = new BiPredicate[Path, BasicFileAttributes] {
      override def test(path: Path, u: BasicFileAttributes): Boolean = {
        def isBefore = path.fileName match {
          case IndexedFile(i) => i.toInt < n
          case _              => false
        }

        path.isFile && isBefore
      }
    }
    Try {
      Files
        .find(saveUnder, 1, predicate)
        .forEach(new Consumer[Path] {
          override def accept(t: Path): Unit = t.delete()
        })
    }
  }
}
