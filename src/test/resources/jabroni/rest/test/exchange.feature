Feature: Exchange should match work with offers

  Background:
    Given the client configuration
    """
    jabroni.client.port = 1234
    """
    And the server configuration
    """
    jabroni.server.port = 1234
    """
    And I start the server
    And I connect a client


  Scenario: Match work with offer
