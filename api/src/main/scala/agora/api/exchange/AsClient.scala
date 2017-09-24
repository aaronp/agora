package agora.api.exchange

import scala.concurrent.{ExecutionContext, Future}

/**
  * Something which can create instances of T from [[Dispatch]] details to send to REST services (or wherever, but that's
  * the intent)
  *
  */
trait AsClient[In, Out] {

  /**
    * Make a request based on the worker details outlined in [[Dispatch]]
    *
    * @param dispatch
    * @return the result of the request
    */
  def dispatch(dispatch: Dispatch[In]): Future[Out]

  /**
    * Mapping of the input parameter
    *
    * @param f  the mapping function of the input parameter
    * @param ec the execution context used to map the futures
    * @tparam A the input type
    * @return a new asClient with A as the input
    */
  final def contramap[A](f: A => In)(implicit ec: ExecutionContext): AsClient[A, Out] = {
    val parent = this
    new AsClient[A, Out] {
      override def dispatch(details: Dispatch[A]): Future[Out] = {
        parent.dispatch(details.copy(request = f(details.request)))
      }
    }
  }

  /**
    * Maps the output type from Out to A
    *
    * @param f  the mapping function
    * @param ec the execution context
    * @tparam A the new return type
    * @return a new asClient with A as the output
    */
  final def flatMap[A](f: Out => Future[A])(implicit ec: ExecutionContext): AsClient[In, A] = {
    val parent = this
    new AsClient[In, A] {
      override def dispatch(details: Dispatch[In]): Future[A] = {
        parent.dispatch(details).flatMap(f)
      }
    }
  }

  /**
    * Maps the output type from Out to A
    *
    * @param f  the mapping function
    * @param ec the execution context
    * @tparam A the new return type
    * @return a new asClient with A as the output
    */
  final def map[A](f: Out => A)(implicit ec: ExecutionContext): AsClient[In, A] = {
    val parent = this
    new AsClient[In, A] {
      override def dispatch(details: Dispatch[In]): Future[A] = {
        parent.dispatch(details).map(f)
      }
    }
  }
}

object AsClient {

  class identity[T] extends AsClient[T, T] {
    override def dispatch(dispatch: Dispatch[T]): Future[T] = Future.successful(dispatch.request)
  }

  class AsClientFunction[In, T](f: Dispatch[In] => Future[T]) extends AsClient[In, T] {
    override def dispatch(details: Dispatch[In]): Future[T] = f(details)
  }

  def pure[T] = new identity[T]

  def apply[In, T](f: Dispatch[In] => Future[T]): AsClient[In, T] = new AsClientFunction(f)

  def sync[In, T](f: Dispatch[In] => T): AsClient[In, T] = apply(f.andThen(Future.successful))

  def async[In, T](f: Dispatch[In] => T)(implicit ec: ExecutionContext): AsClient[In, T] = {
    apply(f.andThen(r => Future(r)))
  }
}
