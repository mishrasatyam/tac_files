package com.tacplatform.settings

import com.typesafe.config.Config
import com.tacplatform.common.state.ByteStr
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader

import scala.concurrent.duration._

case class RewardsSettings(
    term: Int,
    initial: Long,
    minIncrement: Long,
    votingInterval: Int
) {
  require(initial >= 0, "initial must be greater than or equal to 0")
  require(minIncrement > 0, "minIncrement must be greater than 0")
  require(term > 0, "term must be greater than 0")
  require(votingInterval > 0, "votingInterval must be greater than 0")
  require(votingInterval <= term, s"votingInterval must be less than or equal to term($term)")

  def nearestTermEnd(activatedAt: Int, height: Int): Int = {
    require(height >= activatedAt)
    val diff = height - activatedAt + 1
    val mul  = math.ceil(diff.toDouble / term).toInt
    activatedAt + mul * term - 1
  }

  def votingWindow(activatedAt: Int, height: Int): Range = {
    val end   = nearestTermEnd(activatedAt, height)
    val start = end - votingInterval + 1
    if (height >= start) Range.inclusive(start, height)
    else Range(0, 0)
  }
}

object RewardsSettings {
  val MAINNET = apply(
    1000000,
    300000000,
    50000000,
    100000 
  )

  val TESTNET = apply(
    100000,
    6 * Constants.UnitsInTac,
    50000000,
    10000
  )

  val STAGENET = apply(
    100000,
    6 * Constants.UnitsInTac,
    50000000,
    10000
  )
}

case class FunctionalitySettings(
    featureCheckBlocksPeriod: Int,
    blocksForFeatureActivation: Int,
    generationBalanceDepthFrom50To1000AfterHeight: Int = 0,
    blockVersion3AfterHeight: Int = 0,
    preActivatedFeatures: Map[Short, Int] = Map.empty,
    doubleFeaturesPeriodsAfterHeight: Int,
    maxTransactionTimeBackOffset: FiniteDuration = 120.minutes,
    maxTransactionTimeForwardOffset: FiniteDuration = 90.minutes,
    lastTimeBasedForkParameter: Long = 0L,
    leaseExpiration: Int = 1000000,
    estimatorPreCheckHeight: Int = 0,
    minAssetInfoUpdateInterval: Int = 100000,
    minBlockTime: FiniteDuration = 55.seconds,
    delayDelta: Int = 8
) {
  val allowLeasedBalanceTransferUntilHeight: Int        = blockVersion3AfterHeight
  val allowTemporaryNegativeUntil                       = lastTimeBasedForkParameter
  val minimalGeneratingBalanceAfter                     = lastTimeBasedForkParameter
  val allowTransactionsFromFutureUntil                  = lastTimeBasedForkParameter
  val allowUnissuedAssetsUntil                          = lastTimeBasedForkParameter
  val allowInvalidReissueInSameBlockUntilTimestamp      = lastTimeBasedForkParameter
  val allowMultipleLeaseCancelTransactionUntilTimestamp = lastTimeBasedForkParameter

  require(featureCheckBlocksPeriod > 0, "featureCheckBlocksPeriod must be greater than 0")
  require(
    (blocksForFeatureActivation > 0) && (blocksForFeatureActivation <= featureCheckBlocksPeriod),
    s"blocksForFeatureActivation must be in range 1 to $featureCheckBlocksPeriod"
  )
  require(minAssetInfoUpdateInterval >= 0, "minAssetInfoUpdateInterval must be greater than or equal to 0")

  def activationWindowSize(height: Int): Int =
    featureCheckBlocksPeriod * (if (height <= doubleFeaturesPeriodsAfterHeight) 1 else 2)

  def activationWindow(height: Int): Range =
    if (height < 1) Range(0, 0)
    else {
      val ws = activationWindowSize(height)
      Range.inclusive((height - 1) / ws * ws + 1, ((height - 1) / ws + 1) * ws)
    }

  def blocksForFeatureActivation(height: Int): Int =
    blocksForFeatureActivation * (if (height <= doubleFeaturesPeriodsAfterHeight) 1 else 2)

  def generatingBalanceDepth(height: Int): Int =
    if (height >= generationBalanceDepthFrom50To1000AfterHeight) 1000 else 50
}

object FunctionalitySettings {
  val MAINNET = apply(
    featureCheckBlocksPeriod = 5000,
    blocksForFeatureActivation = 4000,
    preActivatedFeatures = (1 to 16).map(_.toShort -> 0).toMap,
    generationBalanceDepthFrom50To1000AfterHeight = 0,
    lastTimeBasedForkParameter = 0L,
    blockVersion3AfterHeight = 0,
    doubleFeaturesPeriodsAfterHeight = 0,
    estimatorPreCheckHeight = 0
  )

  val TESTNET = apply(
    featureCheckBlocksPeriod = 3000,
    blocksForFeatureActivation = 2700,
    blockVersion3AfterHeight = 161700,
    doubleFeaturesPeriodsAfterHeight = Int.MaxValue,
    lastTimeBasedForkParameter = 1492560000000L,
    estimatorPreCheckHeight = 817380
  )

  val STAGENET = apply(
    featureCheckBlocksPeriod = 100,
    blocksForFeatureActivation = 40,
    doubleFeaturesPeriodsAfterHeight = 1000000000,
    preActivatedFeatures = (1 to 13).map(_.toShort -> 0).toMap,
    minAssetInfoUpdateInterval = 10
  )

  val configPath = "tac.blockchain.custom.functionality"
}

case class GenesisTransactionSettings(recipient: String, amount: Long)

case class GenesisSettings(
    blockTimestamp: Long,
    timestamp: Long,
    initialBalance: Long,
    signature: Option[ByteStr],
    transactions: Seq[GenesisTransactionSettings],
    initialBaseTarget: Long,
    averageBlockDelay: FiniteDuration
)

object GenesisSettings { // TODO: Move to network-defaults.conf
  val MAINNET = GenesisSettings(
    1622827769999L,
    1622827769999L,
    19000000000000L,
    ByteStr.decodeBase58("2fXC5CBh5LXSCWKjQs8GhZHJ6KtMB9YZdcQTA7Ea5kzc3Pi2SrRPmyha5RLW9zuSQXnQ4bf51zYtL3Cuyc6fwkFg").toOption,
    List(
      GenesisTransactionSettings("TuC612k4JjiHZ9c3DXyZVJbLWzScBneMfqV", 19000000000000L),
//      GenesisTransactionSettings("TuA2uckBur2Ny5aFBSLR4HfpPpztCmZcBnH", 1000000000000L),
//      GenesisTransactionSettings("Tu5YcFrpZACN6u76HybLwCbxRaLzB3MTvZh", 1000000000000L),
//      GenesisTransactionSettings("TuCjc3EYLEqJjty7YdqFuyPnCA4iQ4km5Tr", 1000000000000L),
//      GenesisTransactionSettings("Tu3b5wr8djumzSvzM67qbMJyJfDRBr2UAyt", 1000000000000L),
//      GenesisTransactionSettings("Tu7Tx4Njimobr72SNpRpcwfiRTTi2urhhUM", 1000000000000L),
//      GenesisTransactionSettings("TuQmx5qFct7kVDkqaBhSMLQAW3ahYKvGJUy", 10000 00000000L),
//      GenesisTransactionSettings("TuHoAtWQ1XsQaFsSFtJEMd8Yzp6h3rHZUT5", 5000 00000000L),
//      GenesisTransactionSettings("TuBhAW4My3q3vyjmMdEp3pk3BsioVLKUWNV", 1000 00000000L),
    ),
    153722867L,
    60.seconds
  )

  val TESTNET = GenesisSettings(
    1460678400000L,
    1478000000000L,
    Constants.UnitsInTac * Constants.TotalTac,
    ByteStr.decodeBase58("5uqnLK3Z9eiot6FyYBfwUnbyid3abicQbAZjz38GQ1Q8XigQMxTK4C1zNkqS1SVw7FqSidbZKxWAKLVoEsp4nNqa").toOption,
    List(
      GenesisTransactionSettings("3My3KZgFQ3CrVHgz6vGRt8687sH4oAA1qp8", (Constants.UnitsInTac * Constants.TotalTac * 0.04).toLong),
      GenesisTransactionSettings("3NBVqYXrapgJP9atQccdBPAgJPwHDKkh6A8", (Constants.UnitsInTac * Constants.TotalTac * 0.02).toLong),
      GenesisTransactionSettings("3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh", (Constants.UnitsInTac * Constants.TotalTac * 0.02).toLong),
      GenesisTransactionSettings("3NCBMxgdghg4tUhEEffSXy11L6hUi6fcBpd", (Constants.UnitsInTac * Constants.TotalTac * 0.02).toLong),
      GenesisTransactionSettings(
        "3N18z4B8kyyQ96PhN5eyhCAbg4j49CgwZJx",
        (Constants.UnitsInTac * Constants.TotalTac - Constants.UnitsInTac * Constants.TotalTac * 0.1).toLong
      )
    ),
    153722867L,
    60.seconds
  )

  val STAGENET = GenesisSettings(
    1561705836768L,
    1561705836768L,
    Constants.UnitsInTac * Constants.TotalTac,
    ByteStr.decodeBase58("2EaaguFPgrJ1bbMAFrPw2bi6i7kqjgvxsFj8YGqrKR7hT54ZvwmzZ3LHMm4qR7i7QB5cacp8XdkLMJyvjFkt8VgN").toOption,
    List(
      GenesisTransactionSettings("3Mi63XiwniEj6mTC557pxdRDddtpj7fZMMw", Constants.UnitsInTac * Constants.TotalTac)
    ),
    5000,
    1.minute
  )
}

case class BlockchainSettings(
    addressSchemeCharacter: Char,
    functionalitySettings: FunctionalitySettings,
    genesisSettings: GenesisSettings,
    rewardsSettings: RewardsSettings
)

private[settings] object BlockchainType {
  val STAGENET = "STAGENET"
  val TESTNET  = "TESTNET"
  val MAINNET  = "MAINNET"
}

object BlockchainSettings {
  implicit val valueReader: ValueReader[BlockchainSettings] =
    (cfg: Config, path: String) => fromConfig(cfg.getConfig(path))

  // @deprecated("Use config.as[BlockchainSettings]", "0.17.0")
  def fromRootConfig(config: Config): BlockchainSettings = config.as[BlockchainSettings]("tac.blockchain")

  private[this] def fromConfig(config: Config): BlockchainSettings = {
    val blockchainType = config.as[String]("type").toUpperCase
    val (addressSchemeCharacter, functionalitySettings, genesisSettings, rewardsSettings) = blockchainType match {
      case BlockchainType.STAGENET =>
        ('S', FunctionalitySettings.STAGENET, GenesisSettings.STAGENET, RewardsSettings.STAGENET)
      case BlockchainType.TESTNET =>
        ('T', FunctionalitySettings.TESTNET, GenesisSettings.TESTNET, RewardsSettings.TESTNET)
      case BlockchainType.MAINNET =>
        ('%', FunctionalitySettings.MAINNET, GenesisSettings.MAINNET, RewardsSettings.MAINNET)
      case _ => // Custom
        val networkId     = config.as[String](s"custom.address-scheme-character").charAt(0)
        val functionality = config.as[FunctionalitySettings](s"custom.functionality")
        val genesis       = config.as[GenesisSettings](s"custom.genesis")
        val rewards       = config.as[RewardsSettings](s"custom.rewards")
        require(functionality.minBlockTime <= genesis.averageBlockDelay, "minBlockTime should be <= averageBlockDelay")
        (networkId, functionality, genesis, rewards)
    }

    BlockchainSettings(
      addressSchemeCharacter = addressSchemeCharacter,
      functionalitySettings = functionalitySettings,
      genesisSettings = genesisSettings,
      rewardsSettings = rewardsSettings
    )
  }
}
