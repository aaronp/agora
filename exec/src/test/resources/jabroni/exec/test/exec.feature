Feature: Exchange should match work with offers

  Scenario: Queueing a job
    When I start an exchange with command line port=1234
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
        "awaitMatch" : false
      },
      "job" : {
         "replyWith" : "this is a return value"
       }
    }
    """
    Then the job queue should be
      | jobId   | submissionUser |
      | some id | Georgina       |
    And the worker queue should be empty

  @ignore
  Scenario: Requesting work
    When I start an exchange with command line port=1234
    And I start a worker with command line details.name=W1 exchange.port=1234
    And worker W1 creates subscription foo with
    """
    {
      "details" : {
        "aboutMe" : {
          "runUser" : "aaron",
          "location" : {
            "host" : "localhost",
            "port" : 1235
          },
          "name" : "worker",
          "id" : "match me"
        }
      },
      "jobMatcher" : "match-all",
      "submissionMatcher" : "match-all"
    }
    """
    And worker W1 asks for 2 work items using subscription foo
    Then the job queue should be
      | jobId    | submissionUser |
      | match me | Georgina       |

    @ignore
  Scenario: Matching a job
    When I submit a job
    """
    {
      "submissionDetails" : {
        "aboutMe" : {
          "submissionUser" : "Georgina",
          "jobId" : "some id"
        },
        "selection" : "select-first",
        "workMatcher" : "match-all",
        "awaitMatch" : true
      },
      "job" : {
         "replyWith" : "this is a return value"
       }
    }
    """
    Then the job queue should be
      | jobId    | submissionUser |
      | match me | Georgina       |
    When worker W1 subscribes with foo
    And worker W1 asks for 2 work items using subscription foo

