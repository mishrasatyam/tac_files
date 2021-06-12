package com.tacplatform.state

import com.tacplatform.block.Block.BlockId
import com.tacplatform.block.MicroBlock
import com.tacplatform.common.state.ByteStr

trait NG {
  def microBlock(id: ByteStr): Option[MicroBlock]

  def bestLastBlockInfo(maxTimestamp: Long): Option[BlockMinerInfo]

  def microblockIds: Seq[BlockId]
}
