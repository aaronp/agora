//import com.typesafe.sbt.site

val repo = "agora"

val username = "aaronp"

name := repo

organization := s"com.github.${username}"

scalaVersion := "2.11.11"

enablePlugins(GitVersioning)

//enablePlugins(GhpagesPlugin)

//enablePlugins(SiteScaladocPlugin)

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
scalafmtVersion in ThisBuild := "1.0.0"

lazy val agora = (project in file(".")).enablePlugins(BuildInfoPlugin).aggregate(apiJVM, apiJS, rest, ui, exec)

lazy val settings = scalafmtSettings

val additionalScalcSettings = List(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-unchecked",
//  "-explaintypes", // Explain type errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:reflectiveCalls", // Allow reflective calls
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xfuture", // Turn on future language features.
  "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match", // Pattern match may not be typesafe.
  "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",     // Warn when nullary methods return Unit.
//  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
)

val baseScalcSettings = List(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-unchecked",
  "-language:reflectiveCalls", // Allow reflective calls
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-Xfuture" // Turn on future language features.
)

val scalacSettings = baseScalcSettings

lazy val docSettings = site.settings ++ ghpages.settings ++ Seq(
  autoAPIMappings := true,
//  unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(core, akka),
  SiteKeys.siteSourceDirectory := file("site"),
  site.addMappingsToSiteDir(mappings in (packageDoc), "latest/api"),
  git.remoteRepo := s"git@github.com:$username/$repo.git"
)

val commonSettings: Seq[Def.Setting[_]] = Seq(
  //version := parentProject.settings.ver.value,
  organization := s"com.github.${username}",
  scalaVersion := "2.11.11",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  scalacOptions ++= scalacSettings,
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := s"${repo}.build",
  assemblyMergeStrategy in assembly := {
    case str if str.contains("JS_DEPENDENCIES")  => MergeStrategy.discard
    case str if str.contains("logback.xml")      => MergeStrategy.discard
    case str if str.contains("application.conf") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports-${name.value}"))
)

test in assembly := {}

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filterNot { _.data.getName == "JS_DEPENDENCIES" }
}

publishMavenStyle := true

lazy val agoraApi = crossProject
  .in(file("api"))
  .settings(name := s"${repo}-api")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.Api)
  .jvmSettings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := s"${repo}.api.version",
    buildInfoOptions += BuildInfoOption.ToMap,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoOptions += BuildInfoOption.BuildTime
  )
  .jsSettings(
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

lazy val rest = project
  .dependsOn(apiJVM % "compile->compile;test->test", ui)
  .settings(commonSettings)
  .settings(mainClass in assembly := Some("agora.rest.exchange.ExchangeMain"))
  .settings(libraryDependencies ++= Dependencies.Rest)

lazy val exec = project
  .dependsOn(rest, rest % "test->test;compile->compile")
  .settings(commonSettings)
  .settings(mainClass in assembly := Some("agora.exec.ExecMain"))
  .settings(libraryDependencies ++= Dependencies.Rest)

lazy val ui = project.dependsOn(apiJS).settings(commonSettings: _*).settings(libraryDependencies ++= Dependencies.UI).enablePlugins(ScalaJSPlugin)

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
  <url>https://github.com/{username}/{}</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>{username}</id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/{username}/{repo}</url>
      </developer>
    </developers>
}
