package com.tacplatform.it

import com.typesafe.config.ConfigFactory.{defaultApplication, defaultReference}
import com.tacplatform.account.KeyPair
import com.tacplatform.block.Block
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.consensus.PoSSelector
import com.tacplatform.database.openDB
import com.tacplatform.events.BlockchainUpdateTriggers
import com.tacplatform.history.StorageFactory
import com.tacplatform.settings._
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.utils.NTP
import monix.execution.UncaughtExceptionReporter
import monix.reactive.Observer
import net.ceedubs.ficus.Ficus._

object BaseTargetChecker {
  def main(args: Array[String]): Unit = {
    implicit val reporter: UncaughtExceptionReporter = UncaughtExceptionReporter.default
    val sharedConfig = Docker.genesisOverride
      .withFallback(Docker.configTemplate)
      .withFallback(defaultApplication())
      .withFallback(defaultReference())
      .resolve()

    val settings          = TacSettings.fromRootConfig(sharedConfig)
    val db                = openDB("/tmp/tmp-db")
    val ntpTime           = new NTP("ntp.pool.org")
    val (blockchainUpdater, _) = StorageFactory(settings, db, ntpTime, Observer.empty, BlockchainUpdateTriggers.noop)
    val poSSelector       = PoSSelector(blockchainUpdater, settings.synchronizationSettings.maxBaseTargetOpt)

    try {
      val genesisBlock = Block.genesis(settings.blockchainSettings.genesisSettings).explicitGet()
      blockchainUpdater.processBlock(genesisBlock, genesisBlock.header.generationSignature)

      NodeConfigs.Default.map(_.withFallback(sharedConfig)).collect {
        case cfg if cfg.as[Boolean]("tac.miner.enable") =>
          val account = KeyPair.fromSeed(cfg.getString("account-seed")).explicitGet()
          val address   = account.toAddress
          val balance   = blockchainUpdater.balance(address, Tac)
          val timeDelay = poSSelector
            .getValidBlockDelay(blockchainUpdater.height, account, genesisBlock.header.baseTarget, balance)
            .explicitGet()

          f"$address: ${timeDelay * 1e-3}%10.3f s"
      }
    } finally ntpTime.close()
  }
}
