package com.tacplatform.state

import com.tacplatform.block.Block.BlockId
import com.tacplatform.common.state.ByteStr

case class BlockMinerInfo(baseTarget: Long, generationSignature: ByteStr, timestamp: Long, blockId: BlockId)
