name := "Xtract"

def versionedScalacOptions(scalaVersion: String) = {
  Seq(
    "-deprecation",
    "-feature",
    "-language:higherKinds",
  ) ++ (if (scalaVersion.startsWith("2.13")) {
    Nil
  } else {
    Seq("-Ypartial-unification")
  })
}

inThisBuild(Seq(
  credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", System.getenv("SONATYPE_USERNAME"), System.getenv("SONATYPE_PASSWORD")),
  usePgpKeyHex("F76A34B7F9338AC82141DD372456B4E851B8B360"),
  developers ++= List(
    Developer("tmccombs", "Thayne McCombs", "", url("https://github.com/tmccombs")),
    Developer("", "Andy Hurd", "", null),
  ),
  licenses += "Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
  homepage := Some(url("https://github.com/lucidsoftware/xtract")),
  organization := "com.lucidchart",
  scmInfo := Some(ScmInfo(url("https://github.com/lucidsoftware/xtract"), "scm:git:git@github.com:lucidsoftware/xtract.git")),
  version := sys.props.getOrElse("build.version", "0-SNAPSHOT"),
  publishMavenStyle := true,
  scalacOptions ++= versionedScalacOptions(scalaVersion.value),
  sonatypeSessionName := s"[sbt-sonatype] xtract-${scalaBinaryVersion.value}-${version.value}",
))

lazy val commonSettings = Seq(
  publishTo := sonatypePublishToBundle.value
)

lazy val specs2Dependency = Seq(
  "org.specs2" %% "specs2-core" % "4.7.1",
  "org.specs2" %% "specs2-mock" % "4.7.1"
)

val catsVersion = "2.+"
lazy val catsDependency = Seq(
  "org.typelevel" %% "cats-macros" % catsVersion,
  "org.typelevel" %% "cats-kernel" % catsVersion,
  "org.typelevel" %% "cats-core" % catsVersion
)

lazy val xtract = project.in(file("xtract-core")).settings(
  name := "xtract",
  commonSettings,
  description := "Library to deserialize Xml to user types.",
  libraryDependencies ++= catsDependency ++ Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
  )
)

lazy val xtractMacros = project.in(file("macros")).settings(
  name := "xtract-macros",
  commonSettings,
  description := "Macros for creating XmlReaders.",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
).dependsOn(xtract)

lazy val xtractTesting = project.in(file("testing")).settings(
  name := "xtract-testing",
  commonSettings,
  description := "Specs2 matchers for xtract.",
  libraryDependencies ++= specs2Dependency
).dependsOn(xtract)

// we have a seperate project for tests, so that we cand depend on
// xtract-testing
lazy val allTests = project.in(file("unit-tests")).settings(
  skip in publish := true,
  libraryDependencies ++= specs2Dependency map (_ % "test")
).dependsOn(xtract % "test", xtractTesting % "test")

lazy val root = project.in(file(".")).aggregate(xtract, xtractMacros, xtractTesting, allTests).settings(
  commonSettings,
  sonatypeBundleRelease / aggregate := false,
  publishArtifact := false,
)
