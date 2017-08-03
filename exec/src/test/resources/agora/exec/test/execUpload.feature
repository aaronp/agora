@Exec
@ExecUpload
Feature: Executor Client

  Scenario: A client can remotely run jobs on a server
    Given An executor service Alpha started with config
    """
    exec.post : 7770
    """
    And An executor service Beta started with config
    """
    exec.post : 7771
    exec.exchange.post : 7770
    exec.includeExchangeRoutes : false
    """
    And Remote client A connected to port 7770
    When client A executes
    """
    /bin/echo hello world
    """
    Then the response text should be hello world
