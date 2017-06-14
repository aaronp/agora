package agora.exec.test

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(
  tags = Array("@Exec"),
  plugin = Array("pretty", "html:target/cucumber/exec-report.html")
)
class ExecTest
