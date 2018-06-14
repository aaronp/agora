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
trait CrudDsl[F[_], ID, T] {
  def run[A](req: CrudRequest[A]): F[A]

  final def create(id: ID, value: T): F[ID] = run(Create[ID, T](id, value))

  final def delete(id: ID): F[Boolean] = run(Delete[ID](id))
}

object CrudDsl {
  import cats.instances.string._

  def io[ID, T: ToBytes](root: Path, logging: Boolean = false): CrudDsl[IO, ID, T] = new CrudDsl[IO, ID, T] {
    // an example which mixes in logging based on a flag
    private val compiled: CrudRequest ~> IO = {
      val specializedForStringKeys = CrudRequest.For[String, T](root)
      if (logging) {
        specializedForStringKeys.IOInterpreter.andThen(new LogInterpreter)
      } else {
        specializedForStringKeys.IOInterpreter
      }
    }
    override def run[A](req: CrudRequest[A]): IO[A] = compiled(req)
  }

  def task[ID, T: ToBytes](root: Path): CrudDsl[Task, ID, T] = new CrudDsl[Task, ID, T] {
    private val compiled: CrudRequest.For[String, T] = CrudRequest.For[String, T](root)
    override def run[A](req: CrudRequest[A]): Task[A] = compiled.TaskInterpreter(req)
  }

  object explain extends CrudDsl[Print, Nothing, Nothing] {
    override def run[A](req: CrudRequest[A]): Print[A] = DocInterpreter(req)
  }

}
