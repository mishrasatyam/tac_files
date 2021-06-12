package com.tacplatform.transaction
import com.tacplatform.block.Block.BlockId
import com.tacplatform.block.{Block, MicroBlock}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.state.Diff
import monix.reactive.Observable

trait BlockchainUpdater {
  def processBlock(block: Block, hitSource: ByteStr, verify: Boolean = true): Either[ValidationError, Seq[Diff]]
  def processMicroBlock(microBlock: MicroBlock, verify: Boolean = true): Either[ValidationError, BlockId]
  def removeAfter(blockId: ByteStr): Either[ValidationError, DiscardedBlocks]
  def lastBlockInfo: Observable[LastBlockInfo]
  def isLastBlockId(id: ByteStr): Boolean
  def shutdown(): Unit
}

case class LastBlockInfo(id: BlockId, height: Int, score: BigInt, ready: Boolean)
