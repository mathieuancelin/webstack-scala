organization := "org.reactivecouchbase.webstack"
name := """webstack-core-scala"""
version := "0.2.0-SNAPSHOT"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "io.undertow" % "undertow-core" % "1.4.14.Final",
  "io.undertow" % "undertow-websockets-jsr" % "1.4.14.Final",
  "com.typesafe.play" %% "play-json" % "2.6.0-M7",
  "com.typesafe.play" %% "twirl-api" % "1.3.0",
  "com.typesafe" % "config" % "1.3.1",
  "org.reflections" % "reflections" % "0.9.10",
  "com.typesafe.akka" %% "akka-actor" % "2.5.1",
  "com.typesafe.akka" %% "akka-stream" % "2.5.1",
  "com.typesafe.akka" %% "akka-http-core" % "10.0.6",
  "com.github.jknack" % "handlebars" % "4.0.6",
  "ch.qos.logback" % "logback-classic" % "1.1.8",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  "commons-codec" % "commons-codec" % "1.10",
  "org.scalatest" %% "scalatest" % "3.0.3" % "test"
)

val local: Def.Initialize[Option[sbt.Resolver]] = version { (version: String) =>
  val localPublishRepo = "./repository"
  if(version.trim.endsWith("SNAPSHOT"))
    Some(Resolver.file("snapshots", new File(localPublishRepo + "/snapshots")))
  else Some(Resolver.file("releases", new File(localPublishRepo + "/releases")))
}

publishTo <<= local

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://reactivecouchbase.org</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>mathieu.ancelin</id>
        <name>Mathieu ANCELIN</name>
        <url>https://github.com/mathieuancelin</url>
      </developer>
    </developers>
  )


// http://www.scala-sbt.org/0.13/docs/Howto-Customizing-Paths.html
