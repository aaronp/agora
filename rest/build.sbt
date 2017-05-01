enablePlugins(CucumberPlugin)

name := "jabroni-rest"

mainClass in(Compile, run) := Some("jabroni.rest.server.RestService")

CucumberPlugin.glue := "classpath:jabroni.rest.test"

CucumberPlugin.features := List("classpath:jabroni.rest.test",
  "rest/src/test/resources/jabroni/rest/test")

///import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}
coverageMinimum := 80

coverageFailOnMinimum := true

(testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))
