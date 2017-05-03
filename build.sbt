name := "jabroni"

scalaVersion := "2.11.8"

enablePlugins(GitVersioning)

resolvers += Resolver.typesafeRepo("releases")

lazy val jabroni: _root_.sbt.Project = (project in file("."))

val aLotOfResolvers = List(
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  Resolver.bintrayRepo("fcomb", "maven"),
  Opts.resolver.mavenLocalFile,
  DefaultMavenRepository,
  Resolver.defaultLocal,
  Resolver.mavenLocal,
  // Resolver.mavenLocal has issues - hence the duplication
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  "Apache Staging" at "https://repository.apache.org/content/repositories/staging/",
  Classpaths.typesafeReleases,
  Classpaths.sbtPluginReleases,
  Resolver.typesafeRepo("releases"),
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
  Resolver.bintrayRepo("hseeberger", "maven"), // for akka circe support
  "Eclipse repositories" at "https://repo.eclipse.org/service/local/repositories/egit-releases/content/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

val commonSettings: Seq[Def.Setting[_]] = Seq(
    //version := parentProject.settings.ver.value,
    scalaVersion := "2.11.8",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"), //, "-Xmx2G"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
    //resolvers ++= moreResolvers,
    (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))
  )


lazy val api = project.
    settings(commonSettings: _*).
    settings(libraryDependencies ++= Dependencies.Api)

lazy val rest = project.
    dependsOn(api, ui).
    settings(commonSettings).
    settings(libraryDependencies ++= Dependencies.Rest)

lazy val ui = project.
  dependsOn(api).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Dependencies.UI).
  enablePlugins(ScalaJSPlugin)
