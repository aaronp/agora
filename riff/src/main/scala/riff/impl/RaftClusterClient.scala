package riff.impl
import riff.raft.RaftMessage

trait RaftClusterClient[F[_]] {

  def apply[A](append: RaftMessage): F[RaftMessage]
}
