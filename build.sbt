val repo = "agora"
name := repo
val username            = "aaronp"
val scalaEleven         = "2.11.8"
val scalaTwelve         = "2.12.6"
val defaultScalaVersion = scalaTwelve
crossScalaVersions := Seq(scalaEleven, scalaTwelve)
organization := s"com.github.${username}"
scalaVersion := defaultScalaVersion
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
enablePlugins(GitVersioning)
autoAPIMappings := true
exportJars := false
javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-XX:MaxMetaspaceSize=1g")
git.useGitDescribe := false

scalacOptions += "-Ypartial-unification"

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

git.gitTagToVersionNumber := { tag: String =>
  if (tag matches "v?[0-9]+\\..*") {
    Some(tag)
  } else None
}

// see http://scalameta.org/scalafmt/
scalafmtOnCompile in ThisBuild := true
scalafmtVersion in ThisBuild := "1.4.0"

// Define a `Configuration` for each project, as per http://www.scala-sbt.org/sbt-site/api-documentation.html
val Api      = config("api")
val Rest     = config("rest")
val RestApi  = config("restApi")
val Exec     = config("exec")
val ExecApi  = config("execApi")
val ExecTest = config("execTest")
val Flow     = config("flow")
val IO       = config("io")
val Config   = config("config")

// see https://github.com/sbt/sbt-ghpages
// this exposes the 'ghpagesPushSite' task
enablePlugins(GhpagesPlugin)
git.remoteRepo := s"git@github.com:$username/$repo.git"
ghpagesNoJekyll := true

enablePlugins(PamfletPlugin)
enablePlugins(SiteScaladocPlugin)

lazy val scaladocSiteProjects =
  List((api, Api), (rest, Rest), (restApi, RestApi), (exec, Exec), (execApi, ExecApi), (execTest, ExecTest), (io, IO), (configProject, Config), (flow, Flow))

lazy val scaladocSiteSettings = scaladocSiteProjects.flatMap {
  case (project, conf) =>
    SiteScaladocPlugin.scaladocSettings(
      conf,
      mappings in (Compile, packageDoc) in project,
      s"api/${project.id}"
    )
}

// val siteWithScaladocAlt = project.in(file("site/scaladoc-alternative"))
//   .settings(scaladocSiteSettings)

lazy val agora = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(PamfletPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    io,
    streamingApi,
    streamingFree,
    streamingMongo,
    streamingMonix,
    streamingHttp4s,
    streamingAkkaHttp,
    streamingUndertow,
    streamingFinch,
    streamingTyrus,
    streamingVertx,
    streamingExample,
    streamingIntegration,
    configProject,
    riff,
    api,
    restApi,
    rest,
    execApi,
    exec,
    execTest,
    flow
  )
  .settings(
    sourceDirectory in Pamflet := sourceDirectory.value / "site",
    siteSubdirName in ScalaUnidoc := "api/latest",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc)
    //,gitRemoteRepo := "git@github.com:aaronp/agora.git"
  )
//settings(sourceDirectory in Pamflet := sourceDirectory.value / "site")

lazy val settings = scalafmtSettings

def additionalScalcSettings = List(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-unchecked",
  //  "-explaintypes", // Explain type errors in more detail.
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

val baseScalacSettings = List(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:reflectiveCalls", // Allow reflective calls
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked",
  "-language:reflectiveCalls", // Allow reflective calls
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  //"-Xlog-implicits",
  "-Xfuture" // Turn on future language features.
)

val scalacSettings = baseScalacSettings

val commonSettings: Seq[Def.Setting[_]] = Seq(
  //version := parentProject.settings.ver.value,
  organization := s"com.github.${username}",
  scalaVersion := defaultScalaVersion,
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  autoAPIMappings := true,
  exportJars := false,
  crossScalaVersions := Seq(scalaEleven, scalaTwelve),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-XX:MaxMetaspaceSize=1g"),
  scalacOptions ++= scalacSettings,
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := s"${repo}.build",
  assemblyMergeStrategy in assembly := {
    case str if str.contains("application.conf") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  // see http://www.scalatest.org/user_guide/using_scalatest_with_sbt
  (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports-${name.value}", "-oN")),
  // put scaladocs under 'api/latest'
  sourceDirectory in Pamflet := sourceDirectory.value / "site",
  siteSubdirName in SiteScaladoc := "api/latest"
)

test in assembly := {}

publishMavenStyle := true

lazy val streamingApi = project
  .in(file("streaming-api"))
  .dependsOn(io % "compile->compile;test->test", configProject % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-api")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudApi)

lazy val streamingTyrus = project
  .in(file("streaming-tyrus"))
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-tyrus")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudTyrus)

lazy val streamingFree = project
  .in(file("streaming-free"))
  .dependsOn(io % "compile->compile;test->test", streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-free")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudFree)

lazy val streamingMongo = project
  .in(file("streaming-mongo"))
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-mongo")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudMongo)

lazy val streamingHttp4s = project
  .in(file("streaming-http4s"))
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-http4s")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudHttp4s)

lazy val streamingUndertow = project
  .in(file("streaming-undertow"))
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-undertow")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudUndertow)

lazy val streamingAkkaHttp = project
  .in(file("streaming-akkahttp"))
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-akkahttp")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudAkkaHttp)

lazy val streamingFinch = project
  .in(file("streaming-finch"))
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-finch")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudFinch)

lazy val streamingVertx = project
  .in(file("streaming-vertx"))
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-vertx")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudVertx)

lazy val streamingExample = project.in(file("streaming-example")).dependsOn(json % "compile->compile;test->test").dependsOn(streamingVertx % "compile->compile;test->test").settings(name := s"${repo}-streaming-example")
  .settings(commonSettings: _*)

lazy val streamingIntegration = project
  .in(file("streaming-verintegrationtx"))
  .dependsOn(streamingApi % "compile->compile;test->test", streamingVertx % "compile->compile;test->test", riff)
  .settings(name := s"${repo}-streaming-integration")
  .settings(commonSettings: _*)

lazy val streamingMonix = project
  .in(file("streaming-monix"))
  .dependsOn(streamingApi % "compile->compile;test->test", streamingFree % "compile->compile;test->test")
  .settings(name := s"${repo}-streaming-monix")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.CrudMonix)

lazy val configProject = project
  .in(file("config"))
  .settings(name := s"${repo}-config")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.Config)

lazy val flow = project
  .in(file("flow"))
  .dependsOn(io % "compile->compile;test->test", configProject % "compile->compile;test->test")
  .settings(name := s"${repo}-flow")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.Flow)

lazy val riff = project
  .in(file("riff"))
  .settings(name := s"${repo}-riff")
  .settings(commonSettings: _*)
  .dependsOn(streamingApi % "compile->compile;test->test")
  .settings(libraryDependencies ++= Dependencies.Riff)

lazy val json = project
  .in(file("json"))
//  .dependsOn(configProject % "test->compile")
  .settings(name := s"${repo}-json")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.Json)

lazy val io = project
  .in(file("io"))
  .settings(name := s"${repo}-io")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.IO)

lazy val api = project
  .in(file("api"))
  .dependsOn(io % "compile->compile;test->test", configProject % "compile->compile;test->test")
  .dependsOn(flow % "compile->compile;test->test", configProject % "compile->compile;test->test")
  .dependsOn(json % "compile->compile;test->test", configProject % "compile->compile;test->test")
  .settings(name := s"${repo}-api")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.Api)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := s"${repo}.api.version",
    buildInfoOptions += BuildInfoOption.ToMap,
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoOptions += BuildInfoOption.BuildTime
  )
  .enablePlugins(BuildInfoPlugin)

lazy val rest = project
  .dependsOn(api % "compile->compile;test->test", restApi % "compile->compile;test->test")
  .settings(commonSettings: _*)
  .settings(mainClass in assembly := Some("agora.rest.exchange.ExchangeMain"))
  .settings(libraryDependencies ++= Dependencies.Rest)

lazy val restApi = project
  .in(file("rest-api"))
  .settings(name := s"${repo}-rest-api")
  .dependsOn(api % "compile->compile;test->test")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.RestApi)

lazy val exec = project
  .settings(name := s"${repo}-exec")
  .dependsOn(rest % "compile->compile;test->test", execApi % "test->test;compile->compile")
  .settings(commonSettings)
  .settings(mainClass in assembly := Some("agora.exec.ExecMain"))
  .settings(libraryDependencies ++= Dependencies.Rest)

lazy val execApi = project
  .settings(name := s"${repo}-exec-api")
  .in(file("exec-api"))
  .dependsOn(restApi % "compile->compile;test->test")
  .settings(commonSettings: _*)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(PamfletPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .settings(libraryDependencies ++= Dependencies.RestApi)

lazy val execTest = project
  .settings(name := s"${repo}-exec-test")
  .in(file("exec-test"))
  .dependsOn(exec % "compile->compile;test->test")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Dependencies.RestApi)

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/
pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/${username}/${repo}
  </url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>
          ${username}
        </id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/${username}/${repo}
        </url>
      </developer>
    </developers>
}
