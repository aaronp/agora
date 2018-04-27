package lupin.pub

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

import scala.concurrent.{Future, Promise}

package object sequenced {
  type Queue = BlockingQueue[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])]


  def newQ(capacity: Int): Queue = new ArrayBlockingQueue[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])](capacity, true)

  def enqueue(commands: Queue, cmd: SubscriberStateCommand, capacitySizeCheckLimit: Int): Future[SubscriberStateCommandResult] = {
    val promise = Promise[SubscriberStateCommandResult]()
    commands.add(cmd -> promise)

    if (commands.size() > capacitySizeCheckLimit) {
      SubscriberStateCommand.conflate(commands)
    }
    promise.future
  }

}
