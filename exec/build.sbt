name := "agora-exec"

enablePlugins(DockerPlugin)

mainClass in(Compile, run) := Some("agora.exec.ExecMain")

(testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))

test in(assembly) := {}

imageNames in docker := Seq(
  ImageName(s"porpoiseltd/${name.value}:latest")
)

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value

  val resDir = (resourceDirectory in Compile).value
  val entrypointPath = resDir.toPath.resolve("exec.sh").toFile

  // we call this 'docker-logback.xml' and then rename to 'logback.xml' in our
  // dockerfile as we don't want to include a 'logback.xml' in our jar itself,
  // as anyone who would pull that in as a dependency wouldn't thank us for that
  // potential classpath conflict
  val logbackFile = resDir.toPath.resolve("docker-logback.xml").toFile

  sLog.value.warn(s"Creating docker file with entrypoint ${entrypointPath.getAbsolutePath}")

  //
  // see https://forums.docker.com/t/is-it-possible-to-pass-arguments-in-dockerfile/14488
  // for passing in args to docker in run (which basically just says to use $@)
  //
  new Dockerfile {
    from("java")
    expose(7770)
    run("mkdir", "-p", "/app/data")
    run("mkdir", "-p", "/app/logs")
    env("DATA_DIR", "/app/data/")
    volume("/app/data")
    volume("/app/config")
    volume("/app/logs")
    maintainer("Aaron Pritzlaff")
    add(logbackFile, "/app/config/logback.xml")
    add(artifact, "/app/bin/agora-exec.jar")
    add(entrypointPath, "/app/bin/exec.sh")
    run("chmod", "777", "/app/bin/exec.sh")
    workDir("/app")
    //entryPoint("java", "-cp", "/config", "-jar", artifactTargetPath)
    entryPoint("/app/bin/exec.sh")
  }
}