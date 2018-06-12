package crud.api

import java.nio.file.Path

import agora.io.ToBytes
import cats.effect.IO
import cats.free.Free
import cats.free.Free._
import cats.{Functor, Show, ~>}
import com.typesafe.scalalogging.StrictLogging
import crud.api.CrudRequest.{DocInterpreter, LogInterpreter, Print}
import monix.eval.Task

import scala.language.{higherKinds, implicitConversions, postfixOps}

// watching this:
// https://www.youtube.com/watch?v=cxMo1RMsD0M
sealed trait CrudRequest[A]

case class Create[ID, T](id: ID, value: T) extends CrudRequest[ID]

case class Delete[ID](id: ID) extends CrudRequest[Boolean]

trait CrudDsl[F[_]] {
  def run[A](req: CrudRequest[A]): F[A]
}

object CrudDsl {

  def io[T: ToBytes](root: Path, logging: Boolean = false): CrudDsl[IO] = new CrudDsl[IO] {
    private val compiled: CrudRequest ~> IO = {
      val specializedForStringKeys = CrudRequest.For[String, T](root)
      if (logging) {
        specializedForStringKeys.IOInterpreter.andThen(new LogInterpreter)
      } else {
        specializedForStringKeys.IOInterpreter
      }
    }

    override def run[A](req: CrudRequest[A]) = compiled(req)
  }

  def task[T: ToBytes](root: Path): CrudDsl[Task] = new CrudDsl[Task] {
    private val compiled: CrudRequest.For[String, T] = CrudRequest.For[String, T](root)

    override def run[A](req: CrudRequest[A]) = compiled.TaskInterpreter(req)
  }

  def explain(): CrudDsl[Print] = new CrudDsl[Print] {
    override def run[A](req: CrudRequest[A]) = DocInterpreter(req)
  }

}

object CrudRequest {

  type Print[A] = String

  type Log[A] = Unit

  final case class For[ID: Show, T: ToBytes](root: Path) {

    import agora.io.implicits._
    import cats.syntax.show._

    def create(id: ID, value: T): Free[CrudRequest, ID] = liftF[CrudRequest, ID](Create[ID, T](id, value))

    def delete(id: ID, value: T): Free[CrudRequest, Boolean] = liftF[CrudRequest, Boolean](Delete[ID](id))

    object IOInterpreter extends LazyInterpreter[IO]

    object TaskInterpreter extends LazyInterpreter[Task]

    private class LazyInterpreter[F[_] : Lift] extends (CrudRequest ~> F) {
      override def apply[A](fa: CrudRequest[A]): F[A] = {
        fa match {
          case Create(id, value) =>
            Lift[F].lift {
              import ToBytes.ops._
              root.resolve(toShow(id).show).bytes = toAllToBytesOps(value).bytes
              id
            }
          case Delete(id) =>
            Lift[F].lift {
              val file = root.resolve(toShow(id).show)
              val existed = file.exists()
              val deleted = existed && !file.delete(true).exists()
              deleted.asInstanceOf[A] // it seems we have to cast to A :-(
            }
        }
      }
    }

  }

  object DocInterpreter extends (CrudRequest ~> Print) {
    override def apply[A](fa: CrudRequest[A]): Print[A] = {
      fa match {
        case Create(id, value) => s"create $id w/ $value"
        case Delete(id) => s"delete $id"
      }
    }
  }

  class LogInterpreter[F[_] : Functor] extends (F ~> F) with StrictLogging {
    override def apply[A](fa: F[A]): F[A] = {
      Functor[F].map(fa) { a =>
        logger.info(s"$fa returned $a")
        a
      }
    }
  }

}
