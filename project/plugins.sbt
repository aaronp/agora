// The Play plugin
//addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.0-M2")

// web plugins

//https://github.com/sbt/sbt-assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.15")

//https://github.com/jrudolph/sbt-dependency-graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("com.waioeka.sbt" % "cucumber-plugin" % "0.1.2")

// https://github.com/scoverage/sbt-scoverage
//sbt clean coverage it:test
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")