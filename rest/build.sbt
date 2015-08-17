mainClass in(Compile, run) := Some("finance.rest.server.RestService")

enablePlugins(CucumberPlugin)

CucumberPlugin.glue := "classpath:finance.rest.test"
CucumberPlugin.features := List("classpath:finance.rest.test",
  "rest/src/test/resources/finance/rest/test")