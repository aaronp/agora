name := "agora-exec-test"

enablePlugins(DockerPlugin, DockerComposePlugin, CucumberPlugin)

//mainClass in(Compile, run) := Some("agora.exec.ExecMain")

CucumberPlugin.glue := "classpath:agora.exec.test"

CucumberPlugin.features := List("classpath:agora.exec.test", "exec-test/src/test/resources/agora/exec/test")

imageNames in docker := Seq(
  ImageName(s"porpoiseltd/${name.value}:latest")
)

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value

  val resDir         = (resourceDirectory in Compile).value
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
    run("mkdir", "-p", "/data")
    run("mkdir", "-p", "/logs")
    env("DATA_DIR", "/data/")
    volume("/data")
    volume("/config")
    volume("/logs")
    maintainer("Aaron Pritzlaff")
    add(logbackFile, "/config/logback.xml")
    add(artifact, "/app/agora-exec.jar")
    add(entrypointPath, "/app/exec.sh")
    run("chmod", "777", "/app/exec.sh")
    workDir("/app")
    //entryPoint("java", "-cp", "/config", "-jar", artifactTargetPath)
    entryPoint("/app/exec.sh")
  }
}
