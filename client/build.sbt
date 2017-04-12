mainClass in(Compile, run) := Some("jabroni.rest.server.RestService")

enablePlugins(CucumberPlugin)

CucumberPlugin.glue := "classpath:jabroni.rest.test"
CucumberPlugin.features := List("classpath:jabroni.rest.test",
  "rest/src/test/resources/jabroni/rest/test")