package crud.free

import java.nio.file.Path

import agora.io.ToBytes
import cats.effect.IO
import cats.~>
import crud.api.{CrudDsl, CrudRequest, Print}
import monix.eval.Task

object CrudInterpreter {

  import CrudFree._
  import cats.instances.string._

  def io[ID, T: ToBytes](root: Path, logging: Boolean = false): CrudDsl[IO, ID, T] = new CrudDsl[IO, ID, T] {
    // an example which mixes in logging based on a flag
    private val compiled: CrudRequest ~> IO = {
      val specializedForStringKeys = CrudFree.For[String, T](root)
      if (logging) {
        specializedForStringKeys.IOInterpreter.andThen(new LogInterpreter)
      } else {
        specializedForStringKeys.IOInterpreter
      }
    }
    override def run[A](req: CrudRequest[A]): IO[A] = compiled(req)
  }

  def task[ID, T: ToBytes](root: Path): CrudDsl[Task, ID, T] = new CrudDsl[Task, ID, T] {
    private val compiled: CrudFree.For[String, T]  = CrudFree.For[String, T](root)
    override def run[A](req: CrudRequest[A]): Task[A] = compiled.TaskInterpreter(req)
  }

  object explain extends CrudDsl[Print, Nothing, Nothing] {
    override def run[A](req: CrudRequest[A]): Print[A] = DocInterpreter(req)
  }
}
