Feature: Order book

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


  Scenario: Read the initial order book
    Then The order book should now be empty

  Scenario: Place a buy order
    When Bob places a BUY order for 3.5kg at £306
    Then The order book should be
      | BUY | 3.5kg | £306 |

  Scenario: Place a sell order
    When Bob places a SELL order for 1kg at £123
    Then The order book should be
      | SELL | 1kg | £123 |

  Scenario: Place two sell orders at difference prices
    When Bob places a SELL order for 1kg at £1
    And Alice places a SELL order for 1kg at £2
    Then The order book should be
      | SELL | 1kg | £1 |
      | SELL | 1kg | £2 |

  Scenario: Place two sell orders at the same price
    When Bob places a SELL order for 1kg at £1
    And Alice places a SELL order for 1kg at £1
    Then The order book should be
      | SELL | 2kg | £1 |

  Scenario: Place two buy orders at the same price
    When Bob places a BUY order for 1kg at £1
    And Alice places a BUY order for 1kg at £1
    Then The order book should be
      | BUY | 2kg | £1 |

  Scenario: Place two buy orders at different prices
    When Bob places a BUY order for 1kg at £1
    And Alice places a BUY order for 1kg at £2
    Then The order book should be
      | BUY | 1kg | £2 |
      | BUY | 1kg | £1 |

  Scenario: Place BUY and SELL orders at the same price
    When Bob places a SELL order for 1kg at £10
    And Alice places a BUY order for 2kg at £10
    Then The order book should be
      | BUY  | 2kg | £10 |
      | SELL | 1kg | £10 |

  Scenario: Place multiple mixed orders
    When user1 places a SELL order for 3.5kg at £306
    And user2 places a SELL order for 1.2kg at £310
    And user3 places a SELL order for 1.5kg at £307
    And user4 places a SELL order for 2.0kg at £306
    When user1 places a BUY order for 3.5kg at £306
    And user2 places a BUY order for 1.2kg at £310
    And user3 places a BUY order for 1.5kg at £307
    And user4 places a BUY order for 2.0kg at £306
    Then The order book should be
      | SELL | 5.5kg | £306 |
      | SELL | 1.5kg | £307 |
      | SELL | 1.2kg | £310 |
      | BUY  | 1.2kg | £310 |
      | BUY  | 1.5kg | £307 |
      | BUY  | 5.5kg | £306 |
