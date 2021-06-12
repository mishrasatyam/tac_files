package com.tacplatform.database

import com.tacplatform.block.Block
import com.tacplatform.common.state.ByteStr
import com.tacplatform.state.Diff

trait Storage {
  def append(diff: Diff, carryFee: Long, totalFee: Long, reward: Option[Long], hitSource: ByteStr, block: Block): Unit
  def lastBlock: Option[Block]
  def rollbackTo(height: Int): Either[String, Seq[(Block, ByteStr)]]
  def safeRollbackHeight: Int
}
