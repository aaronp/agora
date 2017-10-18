@Exec
Feature: Executor Client

  Background:
    Given A running executor service on port 7770
    And Remote client A connected to port 7770

  Scenario: A client can remotely run jobs on a server
    When client A executes
    """
    /bin/echo hello world
    """
    Then the response text should be hello world

  Scenario: A failed client will failover to another
    Given Remote client B connected to port 7770
    When client B executes
    """
    /bin/echo hello world
    """
    Then the response text should be hello world
    When client B executes
    """
    /bin/echo double check
    """
    Then the response text should be double check
    When we kill the actor system for client B
    And client B executes
    """
    /bin/echo I should still work
    """
    Then the response text should be I should still work
