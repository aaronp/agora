package finance.rest.test

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(
  plugin = Array("pretty",
    "html:target/cucumber/test-report.html",
    "json:target/cucumber/test-report.json",
    "junit:target/cucumber/test-report.xml")
//  , glue = Array("classpath:finance.rest.test.steps")
//  , features= Array("classpath:finance.rest.test"
////    ,"rest/src/it/resources/finance/rest/test"
//    ,"../../rest/src/it/resources/finance/rest/test"
//  )
)
class IntegrationTest
