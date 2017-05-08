@Raft
Feature: Raft Leader Election

  Scenario: Starting a cluster
    Given I start The cluster
      | Name | Port | Connect To | Role     |
      | A    | 1000 | 1000       | Follower |
      | B    | 1001 | 1000       | Follower |
    Then The Cluster state should eventually be
      | Name | Role     | Term | Node View |
      | A    | Follower | 1    | A,B       |
      | B    | Follower | 1    | A,B       |


  Scenario: Force triggering an election
    Given I start The cluster
      | Name | Port | Connect To | Role     |
      | A    | 1000 | 1000       | Follower |
      | B    | 1001 | 1000       | Follower |
    Then The Cluster state should eventually be
      | Name | Role     | Term | Node View |
      | A    | Follower | 1    | A,B       |
      | B    | Follower | 1    | A,B       |
    When Node A requests a vote
    Then The Cluster state should eventually be
      | Name | Role     | Term | Node View | Leader |
      | A    | Leader   | 2    | A,B       | A      |
      | B    | Follower | 2    | A,B       | A      |
    When Node B requests a vote
    Then The Cluster state should eventually be
      | Name | Role     | Term | Node View | Leader |
      | A    | Leader   | 3    | A,B       | B      |
      | B    | Follower | 3    | A,B       | B      |


  Scenario: Adding a new node when connecting to the leader
    Given I start The cluster
      | Name | Port | Connect To | Role     |
      | A    | 1000 | 1000       | Leader   |
      | B    | 1001 | 1000       | Follower |
    When Node C is started on port 1002 and registers with Node A
    Then The Cluster state should eventually be
      | Name | Role     | Term | Node View | Leader |
      | A    | Leader   | 1    | A,B,C     | A      |
      | B    | Follower | 1    | A,B,C     | A      |
      | C    | Follower | 1    | A,B,C     | A      |

  Scenario: Adding a new node when connecting to a follower
    Given I start The cluster
      | Name | Port | Connect To | Role     |
      | A    | 1000 | 1000       | Leader   |
      | B    | 1001 | 1000       | Follower |
    When Node C is started on port 1002 and registers with Node B
    Then The Cluster state should eventually be
      | Name | Role     | Term | Node View | Leader |
      | A    | Leader   | 1    | A,B,C     | A      |
      | B    | Follower | 1    | A,B,C     | A      |
      | C    | Follower | 1    | A,B,C     | A      |


  Scenario: Stopping the leader
    Given I start The cluster
      | Name | Port | Connect To | Role     |
      | A    | 1000 | 1000       | Leader   |
      | B    | 1001 | 1000       | Follower |
      | C    | 1002 | 1000       | Follower |
    When Node A is stopped
    Then Node B or C should eventually become the leader
    And All nodes should agree on leader B or C

  Scenario: Stopping and restarting the leader
    Given I start The cluster
      | Name | Port | Connect To | Role     |
      | A    | 1000 | 1000       | Leader   |
      | B    | 1001 | 1000       | Follower |
      | C    | 1002 | 1000       | Follower |
    When Node A is stopped
    Then Node B or C should eventually become the leader
    When Node A is restarted
    Then Node A should be a follower
    And All nodes should agree on leader B or C

