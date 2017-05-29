package miniraft.state

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import io.circe.Encoder
import jabroni.rest.client.RestClient
import miniraft.state.rest.{RaftHttp, RaftJson}

import scala.concurrent.{Future, Promise}

/**
  * A generic representatino of a raft node -- simply something which can
  * respond to RaftRequest messages
  *
  * @tparam T
  */
trait RaftEndpoint[T] {
  def onRequest(req: RaftRequest): Future[RaftResponse] = {
    req match {
      case vote: RequestVote => onVote(vote)
      case append: AppendEntries[T] => onAppend(append)
    }
  }

  def onVote(vote: RequestVote): Future[RequestVoteResponse]

  def onAppend(append: AppendEntries[T]): Future[AppendEntriesResponse]
}

object RaftEndpoint {

  def apply[T: Encoder](client: RestClient)(implicit mat: Materializer) = new Rest[T](client)

  /** Wraps an existing endoping behind an akka actor, which is convenient for concurrency guranatees
    */
  def apply[T: Encoder](sys: ActorSystem, underlying: RaftEndpoint[T])(implicit mat: Materializer) = {
    val actor = sys.actorOf(async.props(underlying), "raftNode")
    new async.ActorClient[T](actor)
  }

  def deferred[T](implicit sys: ActorSystem) = {
    import buffered._
    val actor = sys.actorOf(buffered.props())
    new BufferedClient[T](actor)
  }

  /** Represents an endpoint by sending REST requests
    */
  class Rest[T: Encoder](client: RestClient)(implicit mat: Materializer) extends RaftEndpoint[T] with RaftJson {

    import RestClient.implicits._
    import mat._

    override def onVote(vote: RequestVote): Future[RequestVoteResponse] = {
      client.send(RaftHttp(vote)).flatMap(_.as[RequestVoteResponse])
    }

    override def onAppend(append: AppendEntries[T]): Future[AppendEntriesResponse] = {
      client.send(RaftHttp(append)).flatMap(_.as[AppendEntriesResponse])
    }
  }

  /**
    * contains an actor-based endpoint. Handy for ensuring atomic access to underlying
    * endpoints
    */
  object async {

    def props[T](endpoint: RaftEndpoint[T]) = Props(new ActorBasedEndpoint[T](endpoint))

    case class RequestPromise[R <: RaftResponse](req: RaftRequest, completeWith: Promise[R])

    /**
      * Represents an actor-based raft node as an endpoint
      *
      * @param endpoint
      * @tparam T
      */
    class ActorClient[T](endpoint: ActorRef) extends RaftEndpoint[T] {
      override def onVote(vote: RequestVote) = send[RequestVoteResponse](vote)

      override def onAppend(append: AppendEntries[T]) = send[AppendEntriesResponse](append)

      private def send[R <: RaftResponse](request: RaftRequest): Future[R] = {
        val promise = Promise[R]()
        endpoint ! RequestPromise(request, promise)
        promise.future
      }
    }



    /**
      * Actor which delegates messages to the underlying endpoint
      *
      * @param underlying
      * @tparam T
      */
    class ActorBasedEndpoint[T](underlying: RaftEndpoint[T]) extends Actor {
      implicit def ec = context.dispatcher

      override def receive = {
        case RequestPromise(req, promise) =>
          promise.tryCompleteWith(underlying.onRequest(req))

        case req: RaftRequest =>
          val from = sender()
          val future = underlying.onRequest(req)
          future.onSuccess {
            case resp => from ! resp
          }
      }
    }
  }


  /**
    * represents an unattached endpoint. Raft requests sent will be buffered until the endpoint
    * is initialized w/ an underlying implementation.
    *
    * This can be handy for resolving chicken/egg problems in configuring raft nodes and the
    * cluster of raft nodes with which they communicate.
    *
    * It can also be useful to pause/analyze endpoint messages.
    */
  object buffered {

    def props[T]() = Props(new BufferedRaftEndpoint[T]())

    case class SetEndpoint[T](endpoint: RaftEndpoint[T])


    /**
      * Represents an actor-based raft node as an endpoint
      */
    class BufferedClient[T](buffered: ActorRef) extends RaftEndpoint[T] {
      def setEndpoint(endpoint : RaftEndpoint[T]) = {
        buffered ! SetEndpoint(endpoint)
      }
      override def onVote(vote: RequestVote) = send[RequestVoteResponse](vote)

      override def onAppend(append: AppendEntries[T]) = send[AppendEntriesResponse](append)

      private def send[R <: RaftResponse](request: RaftRequest): Future[R] = {
        val promise = Promise[R]()
        buffered ! async.RequestPromise(request, promise)
        promise.future
      }
    }


    /**
      * An actor which can act as a RaftEndpoint, buffering requests until
      * it is initialized w/ an endpoint to drain those requests too.
      *
      * It's handy to provide for delayed initialisation, pausing/checking messages, etc
      *
      * @tparam T
      */
    class BufferedRaftEndpoint[T]() extends Actor {
      implicit def ec = context.dispatcher

      def forwarding(ref: ActorRef): Receive = {
        case msg => ref.forward(msg)
      }

      override def receive = buffering(Nil)

      def buffering(messages : List[Any]) : Receive = {
        case SetEndpoint(endpoint) =>
          val child = context.actorOf(async.props(endpoint))
          context.become(forwarding(child))
          messages.reverse.foreach(msg => child ! msg)
        case msg => context.become(buffering(msg :: messages))
      }
    }
  }
}

