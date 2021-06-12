package com.tacplatform.settings

import com.typesafe.config.{Config, ConfigFactory}
import com.tacplatform.metrics.Metrics
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration.FiniteDuration

case class TacSettings(
    directory: String,
    ntpServer: String,
    dbSettings: DBSettings,
    extensions: Seq[String],
    extensionsShutdownTimeout: FiniteDuration,
    networkSettings: NetworkSettings,
    walletSettings: WalletSettings,
    blockchainSettings: BlockchainSettings,
    minerSettings: MinerSettings,
    restAPISettings: RestAPISettings,
    synchronizationSettings: SynchronizationSettings,
    utxSettings: UtxSettings,
    featuresSettings: FeaturesSettings,
    rewardsSettings: RewardsVotingSettings,
    metrics: Metrics.Settings,
    config: Config
)

object TacSettings extends CustomValueReaders {
  def fromRootConfig(rootConfig: Config): TacSettings = {
    val tac = rootConfig.getConfig("tac")

    val directory                 = tac.as[String]("directory")
    val ntpServer                 = tac.as[String]("ntp-server")
    val dbSettings                = tac.as[DBSettings]("db")
    val extensions                = tac.as[Seq[String]]("extensions")
    val extensionsShutdownTimeout = tac.as[FiniteDuration]("extensions-shutdown-timeout")
    val networkSettings           = tac.as[NetworkSettings]("network")
    val walletSettings            = tac.as[WalletSettings]("wallet")
    val blockchainSettings        = tac.as[BlockchainSettings]("blockchain")
    val minerSettings             = tac.as[MinerSettings]("miner")
    val restAPISettings           = tac.as[RestAPISettings]("rest-api")
    val synchronizationSettings   = tac.as[SynchronizationSettings]("synchronization")
    val utxSettings               = tac.as[UtxSettings]("utx")
    val featuresSettings          = tac.as[FeaturesSettings]("features")
    val rewardsSettings           = tac.as[RewardsVotingSettings]("rewards")
    val metrics                   = rootConfig.as[Metrics.Settings]("metrics") // TODO: Move to tac section

    TacSettings(
      directory,
      ntpServer,
      dbSettings,
      extensions,
      extensionsShutdownTimeout,
      networkSettings,
      walletSettings,
      blockchainSettings,
      minerSettings,
      restAPISettings,
      synchronizationSettings,
      utxSettings,
      featuresSettings,
      rewardsSettings,
      metrics,
      rootConfig
    )
  }

  def default(): TacSettings = fromRootConfig(ConfigFactory.load())
}
