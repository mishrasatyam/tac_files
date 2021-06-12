import sbt.nio.file.FileAttributes

name := "grpc-server"

libraryDependencies ++= Dependencies.grpc

extensionClasses ++= Seq(
  "com.tacplatform.api.grpc.GRPCServerExtension",
  "com.tacplatform.events.BlockchainUpdates"
)

inConfig(Compile)(
  Seq(
    PB.protoSources in Compile := Seq(PB.externalIncludePath.value),
    includeFilter in PB.generate := new SimpleFileFilter(
      (f: File) =>
        ((** / "tac" / "node" / "grpc" / ** / "*.proto") || (** / "tac" / "events" / ** / "*.proto"))
          .accept(f.toPath, FileAttributes(f.toPath).getOrElse(FileAttributes.NonExistent))
    ),
    PB.targets += scalapb.gen(flatPackage = true) -> sourceManaged.value
  )
)

enablePlugins(RunApplicationSettings, ExtensionPackaging)
