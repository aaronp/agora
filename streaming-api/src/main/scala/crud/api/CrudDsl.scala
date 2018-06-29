package crud.api

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
