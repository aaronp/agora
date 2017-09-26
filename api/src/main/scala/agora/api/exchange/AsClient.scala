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

  /**
    * Ability to summon an 'AsClient' from implicit scope
    * @param asClient the implicit asClient
    * @tparam A
    * @tparam B
    * @return the implicit instance
    */
  def instance[A, B](implicit asClient: AsClient[A, B]): AsClient[A, B] = asClient

  /** lifts the function into an 'AsClient' instance
    * @param f the function to wrap
    * @tparam In the input type
    * @tparam Out the output type
    * @return an AsClient instance for the given function
    */
  def lift[In, Out](f: Dispatch[In] => Future[Out]): AsClient[In, Out] = new AsClientFunction(f)

  /** lifts the function into an 'AsClient' instance using Future.successful
    * @param f the function to wrap
    * @tparam In the input type
    * @tparam Out the output type
    * @return an AsClient instance for the given function
    */
  def sync[In, Out](f: Dispatch[In] => Out): AsClient[In, Out] = lift(f.andThen(Future.successful))

  /** lifts the function into an 'AsClient' instance using Future.apply
    * @param f the function to wrap
    * @tparam In the input type
    * @tparam Out the output type
    * @return an AsClient instance for the given function
    */
  def async[In, Out](f: Dispatch[In] => Out)(implicit ec: ExecutionContext): AsClient[In, Out] = {
    lift(f.andThen(r => Future(r)))
  }
}
