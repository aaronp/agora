enablePlugins(CucumberPlugin)

name := "jabroni-exec"

mainClass in(Compile, run) := Some("jabroni.exec.Main")

CucumberPlugin.glue := "classpath:jabroni.exec.test"

CucumberPlugin.features := List("classpath:jabroni.exec.test",
  "exec/src/test/resources/jabroni/exec/test")

///import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}
coverageMinimum := 80

coverageFailOnMinimum := true

(testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))
