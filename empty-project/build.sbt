import TwirlKeys._

enablePlugins(SbtTwirl)
enablePlugins(JavaServerAppPackaging)

name := """empty-project"""

version := "1.0"

scalaVersion := "2.12.2"

scalaSource in Compile := baseDirectory.value / "app"

scalaSource in Test := baseDirectory.value / "tests"

resourceDirectory in Compile := baseDirectory.value / "res"

resourceDirectory in Test := baseDirectory.value / "res"

sourceDirectories in (Compile, compileTemplates) := Seq(baseDirectory.value / "app")

mainClass in Compile := Some("org.reactivecouchbase.webstack.WebStack")

mainClass in reStart := Some("org.reactivecouchbase.webstack.WebStack")

libraryDependencies += "org.reactivecouchbase.webstack" %% "webstack-core-scala" % "0.2.0-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % "test"

