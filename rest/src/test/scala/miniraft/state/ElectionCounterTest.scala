package miniraft.state

import org.scalatest.FunSuite

class ElectionCounterTest extends FunSuite {}

object ElectionCounterTest {
  def create(size: Int) = new ElectionCounter(size)
}
