androidBuildJar

javacOptions in Compile ++= Seq("-source", "1.7", "-target", "1.7")

javacOptions in (Compile,doc) += "-Xdoclint:none"

javacOptions in (Compile,doc) := {
  (javacOptions in (Compile,doc)).value.foldRight(List.empty[String]) {
    (x, a) => if (x != "-bootclasspath") x :: a else
      x :: a.head + java.io.File.pathSeparator +
        (file(System.getProperty("java.home")) / "lib" / "rt.jar").getAbsolutePath :: a.tail
  }
}

name := "java-futures"

organization := "com.hanhuy.android"

version := "0.3"

platformTarget in Android := "android-19"

debugIncludesTests in Android := false

libraryDependencies += "com.google.guava" % "guava" % "17.0" % "provided"

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
