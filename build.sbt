name := "jabroni"

organization := "com.github.aaronp"

scalaVersion := "2.11.8"

enablePlugins(GitVersioning)

// TODO - as the project stabilizes, improve coverage
coverageMinimum := 50

git.useGitDescribe := false

git.gitTagToVersionNumber := { tag: String =>
  if (tag matches "v?[0-9]+\\..*") {
    Some(tag)
  } else None
}

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-language:reflectiveCalls")

lazy val jabroni = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  aggregate(apiJVM, apiJS, rest, ui, exec)


val commonSettings: Seq[Def.Setting[_]] = Seq(
  //version := parentProject.settings.ver.value,
  organization := "com.github.aaronp",
  scalaVersion := "2.11.8",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"), //, "-Xmx2G"),
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-language:reflectiveCalls"),
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "jabroni.build",
  assemblyMergeStrategy in assembly := {
    case str if str.contains("JS_DEPENDENCIES") => MergeStrategy.concat
    case str if str.contains("logback.xml") => MergeStrategy.discard
    case str if str.contains("application.conf") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports-${name.value}")))


publishMavenStyle := true

lazy val jabroniApi = crossProject.in(file("api")).
  settings(name := "jabroni-api").
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Dependencies.Api).
  jvmSettings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "jabroni.api.version",
    buildInfoOptions += BuildInfoOption.ToMap,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoOptions += BuildInfoOption.BuildTime
  ).
  jsSettings(
    // no JS-specific settings so far
    scalaJSOptimizerOptions ~= {
      _.withDisableOptimizer(true)
    }
  )

lazy val apiJVM = jabroniApi.jvm.enablePlugins(BuildInfoPlugin)

lazy val apiJS = jabroniApi.js

lazy val rest = project.
  dependsOn(apiJVM, ui).
  settings(commonSettings).
  settings(mainClass in assembly := Some("jabroni.rest.exchange.ExchangeMain")).
  settings(libraryDependencies ++= Dependencies.Rest)

lazy val exec = project.
  dependsOn(rest, rest % "test->test;compile->compile").
  settings(commonSettings).
  settings(mainClass in assembly := Some("jabroni.exec.ExecMain")).
  settings(libraryDependencies ++= Dependencies.Rest)

lazy val ui = project.
  dependsOn(apiJS).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Dependencies.UI).
  enablePlugins(ScalaJSPlugin)

assemblyMergeStrategy in assembly := {
  case str if str.contains("JS_DEPENDENCIES") => MergeStrategy.concat
    MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/
pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/aaronp/jabroni</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>aaronp</id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/aaronp/jabroni</url>
      </developer>
    </developers>
}
