name := "jabroni"

organization := "com.github.aaronp"

scalaVersion := "2.11.8"

enablePlugins(GitVersioning)

git.useGitDescribe := false

git.gitTagToVersionNumber := { tag: String =>
  if (tag matches "v?[0-9]+\\..*") {
    if (tag.contains("SNAPSHOT")) {
      Some(tag)
    } else {
      Some(tag)
    }
  } else None
}

lazy val jabroni = (project in file(".")).aggregate(apiJVM, apiJS, rest, ui)

val commonSettings: Seq[Def.Setting[_]] = Seq(
  //version := parentProject.settings.ver.value,
  organization := "com.github.aaronp",
  scalaVersion := "2.11.8",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"), //, "-Xmx2G"),
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
  //resolvers ++= moreResolvers,
  (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports-${name.value}"))
)


lazy val jabroniApi = crossProject.in(file("api")).
  settings(name := "jabroni-api").
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Dependencies.Api).
  jvmSettings(
    // no JVM-specific settings as of yet
  ).
  jsSettings(
    // no JS-specific settings so far
  )

lazy val apiJVM = jabroniApi.jvm

lazy val apiJS = jabroniApi.js

lazy val rest = project.
  dependsOn(apiJVM, ui).
  settings(commonSettings).
  settings(libraryDependencies ++= Dependencies.Rest)

lazy val ui = project.
  dependsOn(apiJS).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Dependencies.UI).
  enablePlugins(ScalaJSPlugin)

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/

// homepage := Some(url("https://github.com/aaronp/jabroni"))

// scmInfo := Some(ScmInfo(url("https://github.com/aaronp/jabroni"), "git@github.com:aaronp/jabroni.git"))

// developers += Developer("aaronp",
//                         "Aaron Pritzlaff",
//                         "aaron.pritzlaff@gmail.com",
//                         url("https://github.com/aaronp/jabroni"))

// licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

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
    <scm>
      <connection>scm:git:github.com/aaronp/jabroni</connection>
      <developerConnection>scm:git:git@github.com:aaronp/jabroni</developerConnection>
      <url>github.com/aaronp/jabroni</url>
    </scm>
    <developers>
      <developer>
        <id>aaronp</id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/aaronp/jabroni</url>
      </developer>
    </developers>
}
