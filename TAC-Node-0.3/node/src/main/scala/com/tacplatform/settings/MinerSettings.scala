package com.tacplatform.settings

import com.tacplatform.mining.Miner

import scala.concurrent.duration.FiniteDuration

case class MinerSettings(
    enable: Boolean,
    quorum: Int,
    intervalAfterLastBlockThenGenerationIsAllowed: FiniteDuration,
    noQuorumMiningDelay: FiniteDuration,
    microBlockInterval: FiniteDuration,
    minimalBlockGenerationOffset: FiniteDuration,
    maxTransactionsInMicroBlock: Int,
    minMicroBlockAge: FiniteDuration
) {
  require(maxTransactionsInMicroBlock <= Miner.MaxTransactionsPerMicroblock)
}
