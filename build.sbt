name := "jabroni"

scalaVersion := "2.11.8"

enablePlugins(GitVersioning)

resolvers += Resolver.typesafeRepo("releases")

lazy val jabroni = (project in file(".")).aggregate(api, rest, ui)

val aLotOfResolvers = List(
  Resolver.defaultLocal
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
