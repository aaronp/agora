import sbt._
import Keys._
import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}

object Common {
  val appVersion = "0.0.1"

  val scalaV = "2.11.8"

  lazy val copyDependencies = TaskKey[Unit]("copy-dependencies")

  def copyDepTask = copyDependencies <<= (update, crossTarget, scalaVersion) map {
    (updateReport, out, scalaVer) =>
      updateReport.allFiles foreach { srcPath =>
        val destPath = out / "lib" / srcPath.getName
        IO.copyFile(srcPath, destPath, preserveLastModified = true)
      }
  }

  val moreResolvers = List(
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
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
    Classpaths.sbtPluginReleases,
    Resolver.bintrayRepo("hseeberger", "maven"), // for akka circe support
    "Eclipse repositories" at "https://repo.eclipse.org/service/local/repositories/egit-releases/content/",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )


  val settings: Seq[Def.Setting[_]] = Seq(
    version := appVersion,
    scalaVersion := Common.scalaV,
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"), //, "-Xmx2G"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
    copyDepTask,
    resolvers ++= moreResolvers,
    coverageMinimum := 30,
    coverageFailOnMinimum := false,
    (testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/scalatest-reports"))
  )
}