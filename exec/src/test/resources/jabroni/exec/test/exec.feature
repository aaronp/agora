@Exec
Feature: Executor Client

  Scenario: A client can remotely run jobs on a server
    Given A running executor service on port 7770
    And Remote client A connected to port 7770
    When client A executes
    """
    /bin/echo hello world
    """
    Then the response text should be hello world

  Scenario: A failed client will failover to another
    Given A running executor service on port 7770
    And Remote client A connected to port 7770
    When client A executes
    """
    /bin/echo hello world
    """
    Then the response text should be hello world
    When client A executes
    """
    /bin/echo double check
    """
    Then the response text should be double check
    When we kill the actor system for client A
    And client A executes
    """
    /bin/echo I should still work
    """
    Then the response text should be I should still work


  Scenario: You can find jobs by their metadata
    Given A running executor service on port 7770
    And Remote client A connected to port 7770
    When client A executes job X with command '/bin/echo testing tags' and the tags
      | key         | value                                                              |
      | foo         | bar                                                                |
      | common      | 123                                                                |
      | description | "This is actually a *long*  /description with £ special characters |
    Then the response text should be testing tags
    When client A executes job Y with command '/bin/echo another job' and the tags
      | key    | value     |
      | foo    | different |
      | common | 123       |
    Then the response text should be testing tags
    When We search jobs with metadata for 'foo' = 'bizz'
    Then no job ids should be returned
    When We search jobs with metadata for 'foo' = 'bar'
    Then we should find job X
    When We search jobs with metadata for 'foo' = 'different'
    Then we should find job Y
    When We search jobs with metadata for 'common' = '123'
    Then we should find jobs X and Y
    When We search jobs with metadata for 'description' = '"This is actually a *long*  /description with £ special characters'
    Then we should find jobs X
    And listing the metadata should return
      | key         | value                                                              |
      | foo         | different                                                          |
      | foo         | bar                                                                |
      | common      | 123                                                                |
      | description | "This is actually a *long*  /description with £ special characters |
