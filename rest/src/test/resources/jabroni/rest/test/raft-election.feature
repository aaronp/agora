@Raft
Feature: Raft Leader Election

  Scenario: Initial vote
    # This'll be nice to have too:
    # ### Given a cluster with nodes A and B
    # Though we'll keep these steps as well so we can create clusters
    # in any state we want
    Given Node A with state
      | current term | state    | voted for | append index | last applied |
      | 1            | Follower |           | 0            | 0            |
    And Node B with state
      | current term | state    | voted for | append index | last applied |
      | 1            | Follower |           | 0            | 0            |
    When Node A has an election timeout
    Then Node A should send the RequestVote message
      | to node | term | last log index | last log term |
      | B       | 2    | 0              | 0             |
    And Node A should be in state
      | current term | state                                       | voted for | append index | last applied |
      | 2            | Candidate(votesFor:[A], votesAgainst:[], 2) | A         | 0            | 0            |
    When Node B receives its RequestVote message, it should reply with
      | to node | term | granted |
      | A       | 2    | true    |
    And Node B should be in state
      | current term | state    | voted for | append index | last applied |
      | 2            | Follower | A         | 0            | 0            |
    When Node A receives its responses
    Then Node A should send the empty AppendEntities messages
      | to node | term | leader id | prev log index | prev log term | leader commit |
      | B       | 2    | A         | 0              | 0             | 0             |
    When Node A receives its responses
    Then Node A should be in state
      | current term | state         | voted for | append index | last applied |
      | 2            | Leader(B:0,1) | A         | 0            | 0            |
    And Node A should have cluster view
      | peer | next index | match index | vote granted |
      | B    | 1          | 0           | true         |
    And no messages should be pending

  Scenario: election when the node's log index is less than the receiving node's index
    Given Node A with state
      | current term | state    | voted for | append index | last applied |
      | 1            | Follower |           | 0            | 0            |
    And Node B with state
      | current term | state    | voted for | append index | last applied |
      | 1            | Follower |           | 1            | 0            |
    When Node A has an election timeout
    Then Node A should send the RequestVote message
      | to node | term | last log index | last log term |
      | B       | 2    | 0              | 0             |
    And Node A should be in state
      | current term | state                                       | voted for | append index | last applied |
      | 2            | Candidate(votesFor:[A], votesAgainst:[], 2) | A         | 0            | 0            |
    When Node B receives its RequestVote message, it should reply with
      | to node | term | granted |
      | A       | 2    | false   |
    And Node B should be in state
      | current term | state    | voted for | append index | last applied |
      | 1            | Follower |           | 1            | 0            |
    When Node A receives its responses
    Then no messages should be pending

  Scenario: election when the node's already voted for someone in that term
    Given Node A with state
      | current term | state    | voted for | append index | last applied |
      | 1            | Follower |           | 0            | 0            |
    And Node B with state
      | current term | state    | voted for | append index | last applied |
      | 2            | Follower | C         | 0            | 0            |
    And Node C with state
      | current term | state                                       | voted for | append index | last applied |
      | 2            | Candidate(votesFor:[C], votesAgainst:[], 3) | C         | 0            | 0            |
    When Node A has an election timeout
    Then Node A should send the RequestVote messages
      | to node | term | last log index | last log term |
      | B       | 2    | 0              | 0             |
      | C       | 2    | 0              | 0             |
    And Node A should be in state
      | current term | state                                       | voted for | append index | last applied |
      | 2            | Candidate(votesFor:[A], votesAgainst:[], 3) | A         | 0            | 0            |
    When Node B receives its RequestVote message, it should reply with
      | to node | term | granted |
      | A       | 2    | false   |
    When Node C receives its RequestVote message, it should reply with
      | to node | term | granted |
      | A       | 2    | false   |
    And Node B should be in state
      | current term | state    | voted for | append index | last applied |
      | 2            | Follower | C         | 0            | 0            |
    When Node A receives its responses
    Then no messages should be pending
    And Node A should be in state
      | current term | state                                          | voted for | append index | last applied |
      | 2            | Candidate(votesFor:[A], votesAgainst:[B,C], 3) | A         | 0            | 0            |
    And Node C should be in state
      | current term | state                                       | voted for | append index | last applied |
      | 2            | Candidate(votesFor:[C], votesAgainst:[], 3) | C         | 0            | 0            |


  Scenario: Initial Append Entries
    Given Node A with state
      | current term | state         | voted for | append index | last applied |
      | 2            | Leader(B:0,0) | A         | 0            | 0            |
    And Node B with state
      | current term | state    | voted for | append index | last applied |
      | 2            | Follower | A         | 0            | 0            |
    # Trigger an append entries from a client to the followers
    When Node A receives a client request to add foo
    Then Node A should be in state
      | current term | state         | voted for | append index | last applied |
      | 2            | Leader(B:0,1) | A         | 0            | 0            |
    Then The log for Node A should be
      | value | term | index | committed |
      | foo   | 2    | 1     | false     |
    Then Node A should send the AppendEntries message
      | to node | term | prev log index | prev log term | append index | entries |
      | B       | 2    | 0              | 0             | 0            | foo     |
    When Node B receives its AppendEntries message, it should reply with
      | to node | term | success | match index |
      | A       | 2    | true    | 1           |
    Then Node B should be in state
      | current term | state    | voted for | append index | last applied |
      | 2            | Follower | A         | 0            | 0            |
    And The log for Node B should be
      | value | term | index | committed |
      | foo   | 2    | 1     | false     |
    When Node A receives its responses
    Then Node A should be in state
      | current term | state         | voted for | append index | last applied |
      | 2            | Leader(B:0,1) | A         | 1            | 1            |
    And Node A should have cluster view
      | peer | next index | match index | vote granted |
      | B    | 2          | 1           | true         |
    And The log for Node A should be
      | value | term | index | committed |
      | foo   | 2    | 1     | true      |
    # Now the 'ack' to the followers to apply their logs
    #
    # I think we can disregard this and trigger an 'append entries' as soon
    # as node A has the majority of responses
    #
    # When Node A has an election timeout
    #
    # This next append entries should commit the logs on the followers:
    Then Node A should send the AppendEntities messages
      | to node | term | leader id | prev log index | prev log term | entries | leader commit |
      | B       | 2    | A         | 1              | 2             |         | 1             |
    When Node B receives its AppendEntries message, it should reply with
      | to node | term | success | match index |
      | A       | 2    | true    | 1           |
    Then Node B should be in state
      | current term | state    | voted for | append index | last applied |
      | 2            | Follower | A         | 1            | 1            |
    And The log for Node B should be
      | value | term | index | committed |
      | foo   | 2    | 1     | true      |
    When Node A receives its responses
    # Node A doesn't need to update its state (or do anything) with these responses
    Then no messages should be pending