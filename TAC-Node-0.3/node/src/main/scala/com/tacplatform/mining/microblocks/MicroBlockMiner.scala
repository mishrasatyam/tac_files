package com.tacplatform.mining.microblocks

import com.tacplatform.account.KeyPair
import com.tacplatform.block.Block
import com.tacplatform.mining.{MinerDebugInfo, MiningConstraint}
import com.tacplatform.settings.MinerSettings
import com.tacplatform.state.Blockchain
import com.tacplatform.transaction.BlockchainUpdater
import com.tacplatform.utx.UtxPool
import io.netty.channel.group.ChannelGroup
import monix.eval.Task
import monix.execution.schedulers.SchedulerService

trait MicroBlockMiner {
  def generateMicroBlockSequence(
      account: KeyPair,
      accumulatedBlock: Block,
      restTotalConstraint: MiningConstraint,
      lastMicroBlock: Long
  ): Task[Unit]
}

object MicroBlockMiner {
  def apply(
      setDebugState: MinerDebugInfo.State => Unit,
      allChannels: ChannelGroup,
      blockchainUpdater: BlockchainUpdater with Blockchain,
      utx: UtxPool,
      settings: MinerSettings,
      minerScheduler: SchedulerService,
      appenderScheduler: SchedulerService,
      nextMicroBlockSize: Int => Int = identity
  ): MicroBlockMiner =
    new MicroBlockMinerImpl(
      setDebugState,
      allChannels,
      blockchainUpdater,
      utx,
      settings,
      minerScheduler,
      appenderScheduler,
      nextMicroBlockSize
    )
}
