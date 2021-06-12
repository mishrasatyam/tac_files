# About
Tac protobuf schemas repository

# How to use
## Java
Add dependency to your `pom.xml`
```xml
<dependency>
    <groupId>com.tacplatform</groupId>
    <artifactId>protobuf-schemas</artifactId>
    <version>{version}</version>
</dependency>
```
## ScalaPB
1. Add dependency to your `build.sbt`: 
```scala
libraryDependencies += ("com.tacplatform" % "protobuf-schemas" % "{version}" classifier "proto") % "protobuf"
```
2. Configure ScalaPB to compile external schemas with:
```scala
inConfig(Compile)(Seq(
   PB.protoSources in Compile := Seq(PB.externalIncludePath.value),
   includeFilter in PB.generate := new SimpleFileFilter((f: File) => f.getName.endsWith(".proto") && f.getParent.endsWith("tac")),
   PB.targets += scalapb.gen(flatPackage = true) -> sourceManaged.value
 ))
```
3. If you use SNAPSHOT version, add this line
```scala
resolvers += Resolver.sonatypeRepo("snapshots")
```
## JavaScript
Npm package: `@tac/protobuf-serialization`.

It contains generated JavaScript classes, TypeScript definitions as well as raw proto files. The default build uses CommonJS and includes all of the proto files. We used `pbjs` to build JavaScript and `pbts` to build TypeScript definitions.

You could also make your own custom build from raw `.proto` files, for example, if you want to use only a subset of proto schemas or gRPC services. They can be found in `@tac/protobuf-serialization/proto` directory.

`long.js` is used for 64-bit integers: `int64`, `uint64`, etc.

Example:
1. `npm install --save @tac/protobuf-serialization`
2. Default build usage
```javascript
import { tac } from '@tac/protobuf-serialization';

const block = new tac.Block();
block.header = // ... set necessary fields

const buffer = tac.Block.encode(block);

const blockDecoded = tac.Block.decode(buffer);
```

## C#
1. Add `App.config`, `packages.config` to your C# solution 
2. Add 
```
  <ItemGroup>
    <Protobuf Include="proto\tac\*.proto" OutputDir="tac\%(RelativePath)" GrpcServices="None" />
    <Protobuf Include="proto\tac\events\*.proto" OutputDir="tac\events\%(RelativePath)" GrpcServices="None" />
    <Protobuf Include="proto\tac\node\grpc\*.proto" OutputDir="tac\node\grpc\%(RelativePath)" GrpcServices="Both" />
  </ItemGroup>
```
to your `.csproj` file. After this just build your project.

or as alternative you can use util protoc, for example:
``` protoc --csharp_out=RelativePath  --proto_path=RelativePathToProtoDir RelativePathToProtoFile```

Also there is a NuGet package TacPlatform.ProtobufSchema with this project.

## Rust
Add dependency to your `Cargo.toml`
```toml
[dependencies]
tac-protobuf-schemas = { git = "https://github.com/tacplatform/protobuf-schemas" }
```

# How to compile locally
## Java
Use `mvn package` to create JAR artifacts:
1. `protobuf-schemas-{version}-proto.jar` - raw .proto files
2. `protobuf-schemas-{version}.jar` - protoc-generated Java classes
