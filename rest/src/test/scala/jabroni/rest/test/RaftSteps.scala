package jabroni.rest.test

import cucumber.api.{DataTable, PendingException}
import cucumber.api.scala.{EN, ScalaDsl}
import miniraft.RaftNode
import org.scalatest.Matchers

class RaftSteps extends ScalaDsl with EN with Matchers with TestData {

  var state = RaftTestState()

  Given("""^I start The cluster$"""){ (clusterTable:DataTable) =>

    clusterTable.toMap.map { row =>
      val name = row("Name")
      val seedNodes = row("Connect To").split(",\\s+", -1).toSet
      val port = row("Port").toInt
      val role = row("Role")
      val node = RaftNode(name)

    }
    //state = state.startCluster()
  }
  Then("""^The Cluster state should eventually be$"""){ (arg0:DataTable) =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  When("""^Node A requests a vote$"""){ () =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  When("""^Node B requests a vote$"""){ () =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  When("""^Node C is started on port (\d+) and registers with Node A$"""){ (arg0:Int) =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  When("""^Node C is started on port (\d+) and registers with Node B$"""){ (arg0:Int) =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  When("""^Node A is stopped$"""){ () =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  Then("""^Node B or C should eventually become the leader$"""){ () =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  Then("""^All nodes should agree on leader B or C$"""){ () =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  When("""^Node A is restarted$"""){ () =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  Then("""^Node A should be a follower$"""){ () =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
}
