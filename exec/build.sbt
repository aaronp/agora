enablePlugins(CucumberPlugin)

name := "agora-exec"

mainClass in(Compile, run) := Some("agora.exec.ExecMain")

CucumberPlugin.glue := "classpath:agora.exec.test"

CucumberPlugin.features := List("classpath:agora.exec.test",
  "exec/src/test/resources/agora/exec/test")

coverageMinimum := 80

coverageFailOnMinimum := false

(testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))
