organization  := "ch.unibas.cs.gravis"

name := "model-masker"

version := "0.1"

scalaVersion := "2.12.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.jcenterRepo

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies += "ch.unibas.cs.gravis" %% "scalismo-faces" % "0.10.1"

libraryDependencies += "org.rogach" %% "scallop" % "2.1.3"

mainClass in assembly := Some("ModelMasker")

assemblyJarName in assembly := "model-masker.jar"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
