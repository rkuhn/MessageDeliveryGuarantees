name := "guarantees"

version := "1.0"

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.3.8",
    "com.typesafe.akka" %% "akka-cluster" % "2.3.8",
    "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.8"
  )
