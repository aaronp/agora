@Exchange
Feature: Exchange should match work with offers

  Scenario: Queueing a job
    When an exchange is started
    And I submit a job
    """
    {
      "submissionDetails" : {
        "aboutMe" : {
          "submissionUser" : "Georgina",
          "jobId" : "some id"
        },
        "selection" : "select-first",
        "workMatcher" : "match-all",
        "awaitMatch" : false,
        "orElse" : []
      },
      "job" : { }
    }
    """
    Then the job queue should be
      | jobId   | submissionUser |
      | some id | Georgina       |
    And the worker queue should be empty

  Scenario: Requesting work
    When an exchange is started with command line port=1234
    And worker W1 is started with command line exchange.port=1234
    And worker W1 creates work subscription foo with
    """
    subscription {
      details.location.port : 5000
      jobMatcher : "match-all"
      submissionMatcher : "match-all"
    }
    """
    And worker W1 asks for 2 work items using subscription foo
    Then the exchange should respond to foo with 0 previous items pending and 2 total items pending
    And the job queue should be empty
    And the worker queue should be
      | subscriptionKey | requested |
      | foo             | 2         |

  Scenario: Matching a job
    When an exchange is started
    And I submit a job
    """
    {
      "submissionDetails" : {
        "aboutMe" : {
          "submissionUser" : "Georgina",
          "jobId" : "match me"
        },
        "selection" : "select-first",
        "workMatcher" : "match-all",
        "awaitMatch" : false,
        "orElse" : []
      },
      "job" : { }
    }
    """
    Then the job queue should be
      | jobId    | submissionUser |
      | match me | Georgina       |
    When worker W1 is started
    And worker W1 creates work subscription bar with
    """
    subscription {
      jobMatcher : "match-all"
      submissionMatcher : "match-all"
    }
    """
    And worker W1 asks for 2 work items using subscription bar
    Then the job queue should be empty
    And the worker queue should be
      | subscriptionKey | requested |
      | bar             | 1         |