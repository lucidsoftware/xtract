name := "Xtract"

val SCALA_211 = "2.11.11"
val SCALA_212 = "2.12.3"

lazy val baseSettings = Seq(
  organization := "com.lucidchart",
  version := sys.props.getOrElse("0-SNAPSHOT")
  scalaVersion := SCALA_212,
  crossScalaVersions := Seq(SCALA_211, SCALA_212),
  fork in test := true
)

lazy val commonSettings = baseSettings ++ Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:higherKinds"
  ),

  libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
  )
)

lazy val publishSettings = commonSettings ++ Seq(
  pomExtra := (
    <url>http://github.com/lucidsoftware/xtract</url>
    <licenses>
      <license>
        <name>Apache License</name>
        <url>http://www.apache.org/licenses/</url>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:lucidsoftware/relate.git</url>
      <connection>scm:git:git@github.com:lucidsoftware/xtract.git</connection>
    </scm>
    <developers>
      <developer>
        <id>tmccombs</id>
        <name>Thayne McCombs</name>
      </developer>
      <developer>
        <name>Andy Hurd</name>
      </developer>
    </developers>
  ),
  pomIncludeRepository := { _ => false },

  pgpPassphrase := Some(Array()),
  pgpPublicRing := (file(System.getProperty("user.home")) / ".pgp" / "pubring"),
  pgpSecretRing := (file(System.getProperty("user.home")) / ".pgp" / "secring"),
  publishMavenStyle := true,
  credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", System.getenv("SONATYPE_USERNAME"), System.getenv("SONATYPE_PASSWORD")),

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT")) {
      Some("snapshots" at nexus + "content/repositories/snapshots")
    } else {
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  }
)

lazy val specs2Dependency = Seq(
  "org.specs2" %% "specs2-core" % "3.9.1",
  "org.specs2" %% "specs2-mock" % "3.9.1"
)

def functionalDep(scalaVersion: String) = scalaVersion match {
  case SCALA_212 => "com.typesafe.play" %% "play-functional" % "2.6.6"
  case _ => "com.typesafe.play" %% "play-functional" % "2.5.15"
}

lazy val xtract = project.in(file("xtract-core")).settings(publishSettings: _*).settings(
  name := "xtract",
  description := "Library to deserialize Xml to user types.",
  libraryDependencies += functionalDep(scalaVersion.value)
)

lazy val xtractTesting = project.in(file("testing")).settings(publishSettings: _*).settings(
  name := "xtract-testing",
  description := "Specs2 matchers for xtract.",
  libraryDependencies ++= specs2Dependency
).dependsOn(xtract)

// we have a seperate project for tests, so that we cand depend on
// xtract-testing
lazy val allTests = project.in(file("unit-tests")).settings(commonSettings: _*).settings(
  publishArtifact := false,
  packagedArtifacts := Map.empty,
  publish := {},
  publishLocal := {},
  libraryDependencies ++= specs2Dependency map (_ % "test")
).dependsOn(xtract % "test", xtractTesting % "test")

lazy val root = project.in(file(".")).aggregate(xtract, xtractTesting, allTests).settings(baseSettings: _*).settings(
  publishArtifact := false
)

/*
scalacOptions in (Compile, doc) ++= Seq(
  "-doc-source-url", "https://github.com/lucidsoftware/xtract/tree/master/€{FILE_PATH}.scala"
)
 */
