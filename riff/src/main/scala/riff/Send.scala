package riff

import riff.raft.RaftRequest


trait Send[F[_]] {
  def apply(r : RaftRequest) : F[r.Reply]
}

