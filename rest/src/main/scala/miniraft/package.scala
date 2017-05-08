import org.slf4j.LoggerFactory

package object miniraft {

  type CommitIndex = Int
  type NodeId = String


  val logger = LoggerFactory.getLogger(getClass)

  def isMajority(n: Int, total: Int) = n > (total / 2)
}
