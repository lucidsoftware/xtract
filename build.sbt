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
  useGpg := true,
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-Ypartial-unification"
  ),
  //isSnapshot := version.value.trim.endsWith("SNAPSHOT")
))

lazy val specs2Dependency = Seq(
  "org.specs2" %% "specs2-core" % "4.0.3",
  "org.specs2" %% "specs2-mock" % "4.0.3"
)

val catsVersion = "1.0.1"
lazy val catsDependency = Seq(
  "org.typelevel" %% "cats-macros" % catsVersion,
  "org.typelevel" %% "cats-kernel" % catsVersion,
  "org.typelevel" %% "cats-core" % catsVersion
)

lazy val xtract = project.in(file("xtract-core")).settings(
  name := "xtract",
  description := "Library to deserialize Xml to user types.",
  libraryDependencies ++= catsDependency ++ Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.1.0"
  )
)

lazy val xtractMacros = project.in(file("macros")).settings(
  name := "xtract-macros",
  description := "Macros for creating XmlReaders.",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
).dependsOn(xtract)

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
  libraryDependencies ++= specs2Dependency map (_ % "test")
).dependsOn(xtract % "test", xtractTesting % "test")

lazy val root = project.in(file(".")).aggregate(xtract, xtractMacros, xtractTesting, allTests).settings(
  publishArtifact := false
)

/*
scalacOptions in (Compile, doc) ++= Seq(
  "-doc-source-url", "https://github.com/lucidsoftware/xtract/tree/master/€{FILE_PATH}.scala"
)
 */
