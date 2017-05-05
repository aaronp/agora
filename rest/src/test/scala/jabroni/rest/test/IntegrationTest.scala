package jabroni.rest.test

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(

  tags = Array("~@ignore"),
  plugin = Array("pretty",
    "html:target/cucumber/test-report.html",
    "json:target/cucumber/test-report.json",
    "junit:target/cucumber/test-report.xml")
)
class IntegrationTest
