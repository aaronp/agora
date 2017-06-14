enablePlugins(CucumberPlugin)

name := "agora-rest"

mainClass in(Compile, run) := Some("agora.rest.exchange.ExchangeMain")

CucumberPlugin.glue := "classpath:agora.rest.test"

CucumberPlugin.features := List("classpath:agora.rest.test",
  "rest/src/test/resources/agora/rest/test")

///import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}
coverageMinimum := 80

coverageFailOnMinimum := true

(testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))
