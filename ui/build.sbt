enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer in Compile := true

scalaJSUseMainModuleInitializer in Test := false

testFrameworks += new TestFramework("utest.runner.Framework")

//http://www.scala-sbt.org/0.13/docs/Howto-Customizing-Paths.html
unmanagedSourceDirectories in Compile ++= Seq(
	baseDirectory.value.getParentFile / "api" / "src" / "main" / "scala",
	baseDirectory.value.getParentFile / "json" / "src" / "main" / "scala"
)

unmanagedResourceDirectories in Compile += {
  baseDirectory.value.getParentFile / "api" / "src" / "main" / "resources"
}

// do this here instead of Dependencies.scala 'cause of the enabled ScalaJSPlugin just for this module
libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi" %%% "utest" % "0.4.5" % "test",
  "be.doeraene" %%% "scalajs-jquery" % "0.9.1",
  "com.lihaoyi" %%% "scalarx" % "0.2.8",
  "com.lihaoyi" %%% "scalatags" % "0.6.1",
  "com.github.japgolly.scalajs-react" %%% "core" % "1.0.0-RC2"
)

scalaJSStage in Global := FullOptStage

skip in packageJSDependencies := false

jsDependencies += RuntimeDOM

