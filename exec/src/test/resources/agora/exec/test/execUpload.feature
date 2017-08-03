@Exec
@ExecUpload
Feature: Executor Client

  Scenario: A client can remotely run jobs on a server
    Given an executor service Alpha started with config
    """
    exec.post : 7770
    exec.initialExecutionSubscription : 1
    """
    And an executor service Beta started with config
    """
    exec.post : 7771
    exec.exchange.post : 7770
    exec.includeExchangeRoutes : false
    exec.initialExecutionSubscription : 1
    """
    And executor client A connects to Alpha
    When client A executes
    """
    /bin/echo hello world
    """
    Then the response text should be hello world
