name := "Xtract"

val scalaVersions = Seq("2.13.4", "2.12.12")

def versionedScalacOptions(scalaVersion: String) = {
  Seq(
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-Xfatal-warnings",
  ) ++ (if (scalaVersion.startsWith("2.13")) {
    Nil
  } else {
    Seq("-Ypartial-unification")
  })
}

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
  sonatypeSessionName := s"[sbt-sonatype] xtract-${scalaBinaryVersion.value}-${version.value}",
))

lazy val commonSettings = Seq(
  scalacOptions ++= versionedScalacOptions(scalaVersion.value),
  publishTo := sonatypePublishToBundle.value
)

lazy val specs2Dependency = Seq(
  "org.specs2" %% "specs2-core" % "4.10.0",
  "org.specs2" %% "specs2-mock" % "4.10.0"
)

lazy val catsDependency = Seq(
  "org.typelevel" %% "cats-macros" % "2.1.1",
  "org.typelevel" %% "cats-core" % "2.3.1"
)

lazy val xtract = (projectMatrix in file("xtract-core"))
  .settings(
    name := "xtract",
    commonSettings,
    description := "Library to deserialize Xml to user types.",
    libraryDependencies ++= catsDependency ++ Seq(
      "org.scala-lang.modules" %% "scala-xml" % "1.3.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.3.2"
    )
  )
  .jvmPlatform(scalaVersions = scalaVersions)

lazy val xtractMacros = (projectMatrix in file("macros"))
  .dependsOn(xtract)
  .settings(
    name := "xtract-macros",
    commonSettings,
    description := "Macros for creating XmlReaders.",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )
  .jvmPlatform(scalaVersions = scalaVersions)

lazy val xtractTesting = (projectMatrix in file("testing"))
  .dependsOn(xtract)
  .settings(
    name := "xtract-testing",
    commonSettings,
    description := "Specs2 matchers for xtract.",
    libraryDependencies ++= specs2Dependency
  )
  .jvmPlatform(scalaVersions = scalaVersions)

// we have a seperate project for tests, so that we can depend on
// xtract-testing
lazy val allTests = (projectMatrix in file("unit-tests"))
  .dependsOn(xtract % "test", xtractMacros % "test", xtractTesting % "test")
  .settings(
    skip in publish := true,
    libraryDependencies ++= specs2Dependency map (_ % "test")
  )
  .jvmPlatform(scalaVersions = scalaVersions)

lazy val root = (project in file("."))
  .aggregate(xtract.projectRefs ++ xtractMacros.projectRefs ++ xtractTesting.projectRefs ++ allTests.projectRefs: _*)
  .settings(
    commonSettings,
    skip in publish := true,
  )
