@Raft
Feature: Raft Append Entries

  @debug
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
      | 2            | Leader(B:0,0) | A         | 1            | 0            |
    And The log for Node A should be
      | value | term | index | committed |
      | foo   | 2    | 0     | false     |
    # check 'prev log term' ... in https://raft.github.io/ the first one is 0
    And Node A should send the AppendEntries message
      | to node | term | leader id | commit index | prev log index | prev log term | entries |
      | B       | 2    | A         | 0            | 0              | 2             | foo     |
    When Node B receives its AppendEntries message, it should reply with
      | to node | term | success | match index |
      | A       | 2    | true    | 1           |
    Then Node B should be in state
      | current term | state    | voted for | append index | last applied |
      | 2            | Follower | A         | 1            | 0            |
    And The log for Node B should be
      | value | term | index | committed |
      | foo   | 2    | 1     | false     |
    When Node A receives its responses
    Then Node A should be in state
      | current term | state         | voted for | append index | last applied |
      | 2            | Leader(B:1,2) | A         | 1            | 1            |
    And Node A should have cluster view
      | peer | next index | match index | vote granted |
      | B    | 2          | 1           | true         |
    And The log for Node A should be
      | value | term | index | committed |
      | foo   | 2    | 1     | true      |
    # Now the 'ack' to the followers to apply their logs
    Then Node A should send the AppendEntries messages
      | to node | term | leader id | commit index | prev log index | prev log term | entries |
      | B       | 2    | A         | 1            | 1              | 2             |         |
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
    Then Node A should have cluster view
      | peer | next index | match index | vote granted |
      | B    | 2          | 1           | true         |
    # Node A doesn't need to update its state (or do anything) with these responses
    And no messages should be pending

