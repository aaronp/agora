name := "agora-ui"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer in Compile := true

scalaJSUseMainModuleInitializer in Test := false

//http://www.scala-sbt.org/0.13/docs/Howto-Customizing-Paths.html
unmanagedSourceDirectories in Compile ++= Seq(

	baseDirectory.value.getParentFile / "api" / "src" / "main" / "scala"
)

unmanagedResourceDirectories in Compile += {
  baseDirectory.value.getParentFile / "api" / "src" / "main" / "resources"
}


//https://github.com/japgolly/scalajs-react/blob/master/doc/USAGE.md
val reactJsDeps =  Seq(
  "org.webjars.bower" % "react" % "15.4.2"
    /        "react-with-addons.js"
    minified "react-with-addons.min.js"
    commonJSName "React",

  "org.webjars.bower" % "react" % "15.4.2"
    /         "react-dom.js"
    minified  "react-dom.min.js"
    dependsOn "react-with-addons.js"
    commonJSName "ReactDOM",

  "org.webjars.bower" % "react" % "15.4.2"
    /         "react-dom-server.js"
    minified  "react-dom-server.min.js"
    dependsOn "react-dom.js"
    commonJSName "ReactDOMServer")


// do this here instead of Dependencies.scala 'cause of the enabled ScalaJSPlugin just for this module
libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi" %%% "utest" % "0.4.5" % "test",
  "be.doeraene" %%% "scalajs-jquery" % "0.9.1",
  "com.lihaoyi" %%% "scalarx" % "0.2.8",
  "com.lihaoyi" %%% "scalatags" % "0.6.1",
  "com.github.japgolly.scalajs-react" %%% "core" % "1.0.0-RC2"
)

test in assembly := {}

scalaJSStage in Global := FullOptStage

skip in packageJSDependencies := false

jsDependencies += RuntimeDOM

