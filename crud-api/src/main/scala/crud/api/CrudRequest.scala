package crud.api

import java.nio.file.Path

import agora.io.ToBytes
import cats.effect.IO
import cats.free.Free
import cats.free.Free._
import cats.{Functor, Show, ~>}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task

import scala.language.{higherKinds, implicitConversions, postfixOps}

// watching this:
// https://www.youtube.com/watch?v=cxMo1RMsD0M
sealed trait CrudRequest[A]

case class Create[ID, T](id: ID, value: T) extends CrudRequest[ID]

case class Delete[ID](id: ID) extends CrudRequest[Boolean]

object CrudRequest {

  final case class For[ID: Show, T: ToBytes](root: Path) {

    import agora.io.implicits._
    import cats.syntax.show._

    def create(id: ID, value: T): Free[CrudRequest, ID] = liftF[CrudRequest, ID](Create[ID, T](id, value))

    def delete(id: ID): Free[CrudRequest, Boolean] = liftF[CrudRequest, Boolean](Delete[ID](id))

    private object IOInterpreterInst extends LazyInterpreter[IO]

    def IOInterpreter: CrudRequest ~> IO = IOInterpreterInst

    private object TaskInterpreterInst extends LazyInterpreter[Task]

    def TaskInterpreter: CrudRequest ~> Task = TaskInterpreterInst

    private class LazyInterpreter[F[_]: Lift] extends (CrudRequest ~> F) {
      override def apply[A](fa: CrudRequest[A]): F[A] = {
        fa match {
          case Create(id, value) =>
            Lift[F].lift {
              import ToBytes.ops._
              root.resolve(id.asInstanceOf[ID].show).bytes = toAllToBytesOps(value.asInstanceOf[T]).bytes
              id.asInstanceOf[A] // it seems we have to cast to A :-(
            }
          case Delete(id) =>
            Lift[F].lift {
              val file    = root.resolve(toShow(id.asInstanceOf[ID]).show)
              val existed = file.exists()
              val deleted = existed && !file.delete(true).exists()
              deleted.asInstanceOf[A] // it seems we have to cast to A :-(
            }
        }
      }
    }
  }

  /**
    * An interpreter which will print out what the program will do
    */
  object DocInterpreter extends (CrudRequest ~> Print) {
    override def apply[A](fa: CrudRequest[A]): Print[A] = {
      fa match {
        case Create(id, value) => Print[A](s"create $id w/ $value", id)
        case Delete(id)        => Print[Boolean](s"delete $id", true).asInstanceOf[Print[A]]
      }
    }
  }

  /**
    * An interpreter which can be composed to add logging
    */
  class LogInterpreter[F[_]: Functor] extends (F ~> F) with StrictLogging {
    override def apply[A](fa: F[A]): F[A] = {
      Functor[F].map(fa) { a =>
        logger.info(s"$fa returned $a")
        a
      }
    }
  }

}
