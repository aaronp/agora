enablePlugins(CucumberPlugin)

name := "agora-exec"

mainClass in(Compile, run) := Some("agora.exec.Main")

CucumberPlugin.glue := "classpath:agora.exec.test"

CucumberPlugin.features := List("classpath:agora.exec.test",
  "exec/src/test/resources/agora/exec/test")

///import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}
coverageMinimum := 80

coverageFailOnMinimum := true

(testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))
