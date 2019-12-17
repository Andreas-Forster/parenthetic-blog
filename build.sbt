organization  := "ch.unibas.cs.gravis"

name := "texture-renderer"

version := "0.1"

scalaVersion := "2.12.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.jcenterRepo

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies += "ch.unibas.cs.gravis" %% "scalismo-faces" % "0.10.1"

libraryDependencies += "org.rogach" %% "scallop" % "2.1.3"

mainClass in assembly := Some("TextureRenderer")

assemblyJarName in assembly := "texture-renderer.jar"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
