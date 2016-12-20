name := """empty-project"""

version := "1.0"

scalaVersion := "2.11.8"

scalaSource in Compile := baseDirectory.value / "app"

scalaSource in Test := baseDirectory.value / "tests"

resourceDirectory in Compile := baseDirectory.value / "res"

resourceDirectory in Test := baseDirectory.value / "res"

mainClass in Compile := Some("org.reactivecouchbase.webstack.WebStack")

libraryDependencies += "org.reactivecouchbase.webstack" %% "webstack-core-scala" % "0.1.0-SNAPSHOT"
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

