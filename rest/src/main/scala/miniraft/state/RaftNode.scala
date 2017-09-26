package miniraft.state

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.typesafe.scalalogging.StrictLogging
import miniraft.state.rest.NodeStateSummary
import miniraft.{UpdateResponse, _}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag

/** A RaftNode encapsulates (or should encapsulate) _any_ raft implementation.
  *
  * The [[RaftEndpoint]] is the API representation for things which want to send
  * raft messages to a node, but a 'RaftNode' can also accept reply messages
  *
  * @tparam T
  */
trait RaftNode[T] extends RaftEndpoint[T] {

  /**
    * Send the given response message to the node
    *
    * @param from     the raft node the message is being sent from -- i.e. the receiver of the request, but originator of the response
    * @param response the response, presumably in reaction to a request
    */
  def onResponse(from: NodeId, response: RaftResponse): Unit

}

object RaftNode {

  def apply[T: ClassTag](logic: RaftNodeLogic[T], protocol: ClusterProtocol)(
      implicit factory: ActorRefFactory): async.RaftNodeActorClient[T] = {
    val nodeProps = async.nodeProps[T](logic, protocol)
    val actor     = factory.actorOf(nodeProps)
    async[T](logic.id, actor)
  }

  /** Create a [[RaftNode]] (which is also a [[LeaderApi]] for the given logic, cluster and timers.
    *
    * If the timers are [[InitialisableTimer]], then they will be initialised using the returned node.
    *
    * @tparam T the command message type represented by the raft node
    * @return a [[miniraft.state.RaftNode.async.RaftNodeActorClient]]
    */
  def apply[T: ClassTag](logic: RaftNodeLogic[T],
                         clusterNodesById: Map[NodeId, RaftEndpoint[T]],
                         electionTimer: RaftTimer,
                         heartbeatTimer: RaftTimer)(
      implicit factory: ActorRefFactory): (async.RaftNodeActorClient[T], async.ActorNodeProtocol[T]) = {

    // chicken/egg ... a protocol which has to know about the node/actor, and the actor needs the protocol
    val protocol =
      new async.ActorNodeProtocol[T](logic.id, clusterNodesById, electionTimer, heartbeatTimer)(factory.dispatcher)
    // egg...
    val actor = factory.actorOf(async.nodeProps[T](logic, protocol))
    protocol.initialise(actor)
    // wrap our actor in a RaftNode[T]
    val node = async[T](logic.id, actor)

    InitialisableTimer.initialise(electionTimer) { _ =>
      node.forceElectionTimeout
    }
    InitialisableTimer.initialise(heartbeatTimer) { _ =>
      node.forceHeartbeatTimeout
    }

    node -> protocol
  }

  object async {

    def apply[T](id: NodeId, raftNodeActor: ActorRef): RaftNodeActorClient[T] =
      new RaftNodeActorClient[T](id, raftNodeActor)

    sealed trait RaftNodeMessage

    private case class LeaderAppendMessage[T](command: T, promise: Promise[UpdateResponse]) extends RaftNodeMessage

    private case class OnResponse(from: NodeId, response: RaftResponse) extends RaftNodeMessage

    private case object OnElectionTimeout extends RaftNodeMessage

    private case class GetProtocol(promise: Promise[ClusterProtocol]) extends RaftNodeMessage

    private case class GetState(promise: Promise[NodeStateSummary]) extends RaftNodeMessage

    private case class OnRequest[R <: RaftResponse](req: RaftRequest, completeWith: Promise[R]) extends RaftNodeMessage

    private case object OnLeaderHeartbeatTimeout extends RaftNodeMessage

    class RaftNodeActorClient[T](val id: NodeId, raftNodeActor: ActorRef) extends RaftNode[T] with LeaderApi[T] {

      def state(): Future[NodeStateSummary] = {
        val promise = Promise[NodeStateSummary]()
        raftNodeActor ! GetState(promise)
        promise.future
      }

      def protocol(): Future[ClusterProtocol] = {
        val promise = Promise[ClusterProtocol]()
        raftNodeActor ! GetProtocol(promise)
        promise.future
      }

      def forceElectionTimeout() = raftNodeActor ! OnElectionTimeout

      def forceHeartbeatTimeout() = raftNodeActor ! OnLeaderHeartbeatTimeout

      override def onResponse(from: NodeId, response: RaftResponse): Unit = {
        raftNodeActor ! OnResponse(from, response)
      }

      override def onVote(vote: RequestVote) = send[RequestVoteResponse](vote)

      override def onAppend(append: AppendEntries[T]) = {
        send[AppendEntriesResponse](append)
      }

      private def send[R <: RaftResponse](request: RaftRequest): Future[R] = {
        val promise = Promise[R]()
        raftNodeActor ! OnRequest(request, promise)
        promise.future
      }

      override def append(command: T) = {
        val promise = Promise[UpdateResponse]()
        raftNodeActor ! LeaderAppendMessage(command, promise)
        promise.future
      }
    }

    def nodeProps[T: ClassTag](logic: RaftNodeLogic[T], protocol: ClusterProtocol): Props = {
      Props(new RaftNodeActor[T](logic, protocol))
    }

    class RaftNodeActor[T: ClassTag](logic: RaftNodeLogic[T], protocol: ClusterProtocol)
        extends Actor
        with StrictLogging {
      implicit def ec = context.dispatcher

      def onMessage(msg: RaftNodeMessage): Unit = msg match {
        case OnRequest(req, promise) => promise.trySuccess(logic.onRequest(req, protocol))
        case LeaderAppendMessage(command: T, promise) =>
          if (!logic.isLeader) {
            val exp = NotTheLeaderException(logic.leaderId, logic.id, logic.raftState.role.name, protocol.clusterSize)
            promise.trySuccess(UpdateResponse(Future.failed(exp)))
          } else {
            val newAck: UpdateResponse.Appendable = logic.onClientRequestToAdd(command, protocol)
            promise.trySuccess(newAck)
          }
        case OnElectionTimeout          => logic.onElectionTimeout(protocol)
        case OnLeaderHeartbeatTimeout   => logic.onLeaderHeartbeatTimeout(protocol)
        case OnResponse(from, received) => logic.onResponse(from, received, protocol)
        case GetProtocol(promise)       => promise.trySuccess(protocol)
        case GetState(promise) =>
          val summary = NodeStateSummary(logic, protocol)
          promise.tryCompleteWith(summary)
      }

      def handler(): Receive = {
        logger.debug(s"${logic.id} w/ ${logic.pendingLeaderAcks.size} pending leader acks")

        //
        {
          case msg: RaftNodeMessage =>
            logger.trace(s"${logic.id} received: $msg")
            onMessage(msg)
        }
      }

      override def receive: Receive = handler()
    }

    private[state] class ActorNodeProtocol[T](
        ourNodeId: NodeId,
        clusterNodesById: Map[NodeId, RaftEndpoint[T]],
        raftElectionTimer: RaftTimer,
        raftHeartbeatTimer: RaftTimer)(override implicit val executionContext: ExecutionContext)
        extends ClusterProtocol.BaseProtocol[T](ourNodeId: NodeId,
                                                clusterNodesById: Map[NodeId, RaftEndpoint[T]],
                                                raftElectionTimer: RaftTimer,
                                                raftHeartbeatTimer)
        with StrictLogging {
      private var raftNodeActor: ActorRef = null

      private[state] def initialise(ref: ActorRef) = {
        require(raftNodeActor == null, "already initialised")
        raftNodeActor = ref
      }

      override def onResponse(from: NodeId,
                              endpoint: RaftEndpoint[T],
                              raftRequest: RaftRequest,
                              response: RaftResponse): Unit = {
        logger.debug(s"$from replied to $ourNodeId with $response")
        raftNodeActor ! OnResponse(from, response)
      }
    }
  }
}
