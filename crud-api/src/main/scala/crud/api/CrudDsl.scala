package crud.api

import java.nio.file.Path

import agora.io.ToBytes
import cats.effect.IO
import cats.~>
import crud.api.CrudRequest.{DocInterpreter, LogInterpreter}
import monix.eval.Task

/**
  * Provides a typical "service" for our Crud algebra, parameterized on F
  * (e.g. more of a tagless final approach)
  *
  * @tparam F
  */
trait CrudDsl[F[_]] {
  def run[A](req: CrudRequest[A]): F[A]

  final def create[ID, T](id: ID, value: T): F[ID] = run(Create[ID, T](id, value))

  final def delete[ID, T](id: ID): F[Boolean] = run(Delete[ID](id))
}

object CrudDsl {
  import cats.instances.string._

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

  object explain extends CrudDsl[Print] {
    override def run[A](req: CrudRequest[A]) = DocInterpreter(req)
  }

}
