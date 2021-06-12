package com.tacplatform.consensus

import com.tacplatform.account.Address
import com.tacplatform.block.Block
import com.tacplatform.block.Block.BlockId
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.state.Blockchain

object GeneratingBalanceProvider {
  private val MinimalEffectiveBalanceForGenerator1: Long = 1000000000000L
  private val MinimalEffectiveBalanceForGenerator2: Long = 100000000000L
  private val FirstDepth                                 = 50
  private val SecondDepth                                = 1000

  def isMiningAllowed(blockchain: Blockchain, height: Int, effectiveBalance: Long): Boolean = {
    val activated = blockchain.activatedFeatures.get(BlockchainFeatures.SmallerMinimalGeneratingBalance.id).exists(height >= _)
    (!activated && effectiveBalance >= MinimalEffectiveBalanceForGenerator1) || (activated && effectiveBalance >= MinimalEffectiveBalanceForGenerator2)
  }

  //noinspection ScalaStyle
  def isEffectiveBalanceValid(blockchain: Blockchain, height: Int, block: Block, effectiveBalance: Long): Boolean =
    block.header.timestamp < blockchain.settings.functionalitySettings.minimalGeneratingBalanceAfter || (block.header.timestamp >= blockchain.settings.functionalitySettings.minimalGeneratingBalanceAfter && effectiveBalance >= MinimalEffectiveBalanceForGenerator1) ||
      blockchain.activatedFeatures
        .get(BlockchainFeatures.SmallerMinimalGeneratingBalance.id)
        .exists(height >= _) && effectiveBalance >= MinimalEffectiveBalanceForGenerator2

  def balance(blockchain: Blockchain, account: Address, blockId: Option[BlockId] = None): Long = {
    val height = blockId.flatMap(blockchain.heightOf).getOrElse(blockchain.height)
    val depth  = if (height >= blockchain.settings.functionalitySettings.generationBalanceDepthFrom50To1000AfterHeight) SecondDepth else FirstDepth
    blockchain.effectiveBalance(account, depth, blockId)
  }
}
