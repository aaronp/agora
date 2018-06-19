package crud.api

import cats.effect.IO
import monix.eval.Task

sealed trait Lift[F[_]] {
  def lift[A](a: => A): F[A]
}
object Lift {
  def apply[F[_]](implicit lift: Lift[F]) = lift
  implicit object TaskLift extends Lift[Task] {
    override def lift[A](a: => A): Task[A] = Task(a)
  }
  implicit object IOLift extends Lift[IO] {
    override def lift[A](a: => A): IO[A] = IO(a)
  }
}
