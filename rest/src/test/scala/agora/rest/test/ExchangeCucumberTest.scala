package agora.rest.test

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(
  tags = Array("@Exchange"),
  plugin = Array("pretty", "html:target/cucumber/test-report.html")
)
class ExchangeCucumberTest
