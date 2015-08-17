val namePrefix = "gaunch"

name := namePrefix + "-root"

version := "0.0.1"

scalaVersion := Common.scalaV

resolvers += Resolver.typesafeRepo("releases")

lazy val api = project.
  settings(Common.settings: _*).
  settings(name := namePrefix + "-api").
  settings(libraryDependencies ++= Dependencies.apiDependencies)

lazy val domain = project.
  dependsOn(api).
  settings(Common.settings: _*).
  settings(name := namePrefix + "-domain").
  settings(libraryDependencies ++= Dependencies.domainDependencies)

lazy val json = project.
  dependsOn(api).
  settings(Common.settings: _*).
  settings(name := namePrefix + "-json").
  settings(libraryDependencies ++= Dependencies.jsonDependencies)

lazy val rest = project.
  dependsOn(api, domain, ui, json).
//  configs(IntegrationTest).
//  settings(Defaults.itSettings).
  settings(Common.settings: _*).
  settings(name := namePrefix + "-rest").
  settings(libraryDependencies ++= Dependencies.restDependencies)

lazy val ui = project.
  dependsOn(api, json).
  settings(Common.settings: _*).
  settings(name := namePrefix + "-ui").
  settings(libraryDependencies ++= Dependencies.uiDependencies).
  enablePlugins(ScalaJSPlugin)

lazy val root = (project in file(".")).
  aggregate(api, domain, ui, json, rest)