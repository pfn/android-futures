import android.Keys._

android.Plugin.androidBuild

name := "android-futures"

organization := "com.hanhuy"

autoScalaLibrary := false

version := "0.1-SNAPSHOT"

platformTarget in Android := "android-19"

debugIncludesTests in Android := false

javacOptions in (Compile,doc) := Nil

publishArtifact in (Compile,packageBin) := true

publishArtifact in (Compile,packageSrc) := true

libraryDependencies += "com.google.guava" % "guava" % "17.0"

// sonatype publishing options follow
publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

pomExtra :=
  <scm>
    <url>git@github.com:pfn/android-futures.git</url>
    <connection>scm:git:git@github.com:pfn/android-futures.git</connection>
  </scm>
  <developers>
    <developer>
      <id>pfnguyen</id>
      <name>Perry Nguyen</name>
      <url>https://github.com/pfn</url>
    </developer>
  </developers>

licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))

homepage := Some(url("https://github.com/pfn/android-futures"))
