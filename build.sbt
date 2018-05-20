name := """coin-predict"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
val specsVersion = "3.9.5"

resolvers += Resolver.sonatypeRepo("snapshots")

// cannot use scala 2.12 since spark only support up to 2.11
scalaVersion := "2.11.12"

libraryDependencies += ws
libraryDependencies += guice
libraryDependencies += ehcache
libraryDependencies += cacheApi


libraryDependencies += "com.h2database" % "h2" % "1.4.196"
libraryDependencies += "org.apache.spark" %% "spark-mllib" % "2.2.0"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.8.7"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7"
dependencyOverrides += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.7"

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
libraryDependencies += "org.specs2" %% "specs2-core" % specsVersion % Test
libraryDependencies += "org.specs2" %% "specs2-mock" % specsVersion % Test

