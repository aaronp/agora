package agora.rest

import miniraft.state.TestCluster.TestClusterNode

package object test {

  type TestNodeLogic = TestClusterNode[String]

}
