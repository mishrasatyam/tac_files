package com.tacplatform.protobuf

package object block {
  type PBBlock = com.tacplatform.protobuf.block.Block
  val PBBlock = com.tacplatform.protobuf.block.Block

  type VanillaBlock = com.tacplatform.block.Block
  val VanillaBlock = com.tacplatform.block.Block

  type PBBlockHeader = com.tacplatform.protobuf.block.Block.Header
  val PBBlockHeader = com.tacplatform.protobuf.block.Block.Header

  type VanillaBlockHeader = com.tacplatform.block.BlockHeader
  val VanillaBlockHeader = com.tacplatform.block.BlockHeader

  type PBSignedMicroBlock = com.tacplatform.protobuf.block.SignedMicroBlock
  val PBSignedMicroBlock = com.tacplatform.protobuf.block.SignedMicroBlock

  type PBMicroBlock = com.tacplatform.protobuf.block.MicroBlock
  val PBMicroBlock = com.tacplatform.protobuf.block.MicroBlock

  type VanillaMicroBlock = com.tacplatform.block.MicroBlock
  val VanillaMicroBlock = com.tacplatform.block.MicroBlock
}
