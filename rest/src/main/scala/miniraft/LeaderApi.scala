package miniraft

import scala.concurrent.Future

trait LeaderApi[T] {

  def append(command: T): Future[UpdateResponse]

}
