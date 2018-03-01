name := "Xtract"

inThisBuild(Seq(
  credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", System.getenv("SONATYPE_USERNAME"), System.getenv("SONATYPE_PASSWORD")),
  developers ++= List(
    Developer("tmccombs", "Thayne McCombs", "", url("https://github.com/tmccombs")),
    Developer("", "Andy Hurd", "", null),
  ),
  licenses += "Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
  homepage := Some(url("https://github.com/lucidsoftware/xtract")),
  organization := "com.lucidchart",
  scmInfo := Some(ScmInfo(url("https://github.com/lucidsoftware/xtract"), "scm:git:git@github.com:lucidsoftware/xtract.git")),
  version := sys.props.getOrElse("build.version", "0-SNAPSHOT"),
  fork in test := true,
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:higherKinds"
  ),
  //isSnapshot := version.value.trim.endsWith("SNAPSHOT")
))

lazy val specs2Dependency = Seq(
  "org.specs2" %% "specs2-core" % "4.0.3",
  "org.specs2" %% "specs2-mock" % "4.0.3"
)

def functionalDep(scalaVersion: String) = {
  if (scalaVersion.startsWith("2.12")) {
    "com.typesafe.play" %% "play-functional" % "2.6.8"
  } else {
    "com.typesafe.play" %% "play-functional" % "2.5.16"
  }
}

lazy val xtract = project.in(file("xtract-core")).settings(
  name := "xtract",
  description := "Library to deserialize Xml to user types.",
  libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
    functionalDep(scalaVersion.value),
  )
)

lazy val xtractTesting = project.in(file("testing")).settings(
  name := "xtract-testing",
  description := "Specs2 matchers for xtract.",
  libraryDependencies ++= specs2Dependency
).dependsOn(xtract)

// we have a seperate project for tests, so that we cand depend on
// xtract-testing
lazy val allTests = project.in(file("unit-tests")).settings(
  publishArtifact := false,
  packagedArtifacts := Map.empty,
  publish := {},
  publishLocal := {},
  // parallel execution leads to a deadlock,
  // see https://github.com/mockito/mockito/issues/1067
  parallelExecution := false,
  libraryDependencies ++= specs2Dependency map (_ % "test")
).dependsOn(xtract % "test", xtractTesting % "test")

lazy val root = project.in(file(".")).aggregate(xtract, xtractTesting, allTests).settings(
  publishArtifact := false
)

/*
scalacOptions in (Compile, doc) ++= Seq(
  "-doc-source-url", "https://github.com/lucidsoftware/xtract/tree/master/€{FILE_PATH}.scala"
)
 */
