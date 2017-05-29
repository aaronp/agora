package miniraft

package object state {


  type NodeId = String
  type LogIndex = Int

//  trait Command

  def isMajority(n: Int, total: Int): Boolean = {
    if (n == 0) {
      total == 0
    } else {
      n > (total >> 1)
    }
  }
}
