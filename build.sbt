name := "agora"

organization := "com.github.aaronp"

scalaVersion := "2.11.11"

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

// see http://scalameta.org/scalafmt/
scalafmtOnCompile in ThisBuild := true
scalafmtVersion in ThisBuild  := "1.0.0-RC3"

lazy val agora = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  aggregate(apiJVM, apiJS, rest, ui, exec)

lazy val settings = scalafmtSettings

val commonSettings: Seq[Def.Setting[_]] = Seq(
  //version := parentProject.settings.ver.value,
  organization := "com.github.aaronp",
  scalaVersion := "2.11.8",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"), //, "-Xmx2G"),
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-language:reflectiveCalls"),
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "agora.build",
  assemblyMergeStrategy in assembly := {
    case str if str.contains("JS_DEPENDENCIES") => MergeStrategy.discard
    case str if str.contains("logback.xml") => MergeStrategy.discard
    case str if str.contains("application.conf") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports-${name.value}")))

test in assembly := {}

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filterNot {_.data.getName == "JS_DEPENDENCIES"}
}

publishMavenStyle := true

lazy val agoraApi = crossProject.in(file("api")).
  settings(name := "agora-api").
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Dependencies.Api).
  jvmSettings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "agora.api.version",
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

lazy val apiJVM = agoraApi.jvm.enablePlugins(BuildInfoPlugin)

lazy val apiJS = agoraApi.js

//
//val apiForIDE = (project in file("api/shared"))
//  .settings(name := "agora-api-forIDE").
//  settings(commonSettings: _*).
//  settings(libraryDependencies ++= Dependencies.Api)

lazy val rest = project.
  dependsOn(apiJVM % "compile->compile;test->test", ui).
  settings(commonSettings).
  settings(mainClass in assembly := Some("agora.rest.exchange.ExchangeMain")).
  settings(libraryDependencies ++= Dependencies.Rest)

lazy val exec = project.
  dependsOn(rest, rest % "test->test;compile->compile").
  settings(commonSettings).
  settings(mainClass in assembly := Some("agora.exec.ExecMain")).
  settings(libraryDependencies ++= Dependencies.Rest)

lazy val ui = project.
  dependsOn(apiJS).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Dependencies.UI).
  enablePlugins(ScalaJSPlugin)

assemblyMergeStrategy in assembly := {
  case str if str.contains("JS_DEPENDENCIES") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/
pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/aaronp/agora</url>
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
        <url>https://github.com/aaronp/agora</url>
      </developer>
    </developers>
}
