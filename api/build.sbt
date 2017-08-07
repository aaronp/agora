
// see https://github.com/sbt/sbt-assembly/blob/master/README.md
assemblyMergeStrategy in assembly := {
  case "JS_DEPENDENCIES" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

scalaJSLinkerConfig ~= { _.withOptimizer(false) }

scalaJSOptimizerOptions ~= { _.withDisableOptimizer(true) }
