@Exec
@ExecUpload
Feature: Executor Client

  Scenario: A client can remotely run jobs on a server
    Given an executor service Alpha started with config
    """
    port : 7770
    initialExecutionSubscription : 1
    """
    And an executor service Beta started with config
    """
    port : 7771
    exchange.port : 7770
    includeExchangeRoutes : false
    initialExecutionSubscription : 1
    """
    And executor client A connects to Alpha
    When client A executes
    """
    /bin/echo hello world
    """
    Then the response text should be hello world
