Feature: Cancel and order

  Background:
    Given the client configuration
    """
    finance.client.port = 1234
    """
    And the server configuration
    """
    finance.server.port = 1234
    """
    And I start the server
    And I connect a client


  Scenario: Cancel a buy order
    When Bob places a BUY order for 3.5kg at £306
    Then The order book should be
      | BUY | 3.5kg | £306 |
    When Bob cancels his last order
    Then The order book should now be empty

  Scenario: Cancel a sell order
    When Bob places a SELL order for 3.5kg at £306
    Then The order book should be
      | SELL | 3.5kg | £306 |
    When Bob cancels his last order
    Then The order book should now be empty

  Scenario: Cancel an order which has been aggregated with another
    When Bob places a SELL order for 1kg at £123
    When Bob places a SELL order for 2kg at £123
    Then The order book should be
      | SELL | 3kg | £123 |
    When Bob cancels his last order
    Then The order book should be
      | SELL | 1kg | £123 |
    When Bob cancels his first order
    Then The order book should now be empty

  Scenario: Cancel an order which doesn't exist
    When Bob places a SELL order for 1kg at £1
    And Bob cancels his SELL order for 1kg at £2
    Then the cancel should return false
    And The order book should be
      | SELL | 1kg | £1 |

  Scenario: Cancel an order for a different user
    When Bob places a SELL order for 1kg at £1
    And Alice cancels his SELL order for 1kg at £1
    And The order book should be
      | SELL | 1kg | £1 |
    And Bob cancels his SELL order for 1kg at £1
    Then The order book should now be empty


  Scenario: Cancel all a users orders
    When Eleanor places a SELL order for 1kg at £2
    And Eleanor places a BUY order for 3kg at £4
    And Georgina places a SELL order for 5kg at £6
    And Georgina places a BUY order for 7kg at £8
    Then The order book should be
      | SELL | 1kg | £2 |
      | SELL | 5kg | £6 |
      | BUY  | 7kg | £8 |
      | BUY  | 3kg | £4 |
    When Georgina cancels all her orders
    Then The order book should be
      | SELL | 1kg | £2 |
      | BUY  | 3kg | £4 |
