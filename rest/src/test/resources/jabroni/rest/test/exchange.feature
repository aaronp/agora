Feature: Exchange should match work with offers

  Background:
    Given I start an exchange with command line port=1234
    And I start a worker with command line details.name=A exchange.port=1234
    When worker A subscribes


  Scenario: Match work with offer
