package com.tacplatform.mining

import com.typesafe.config.ConfigFactory
import com.tacplatform.account.KeyPair
import com.tacplatform.block.{Block, SignedBlockHeader}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.consensus.PoSSelector
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.settings._
import com.tacplatform.state.diffs.ENOUGH_AMT
import com.tacplatform.state.{BalanceSnapshot, BlockMinerInfo, Blockchain, NG}
import com.tacplatform.transaction.BlockchainUpdater
import com.tacplatform.transaction.TxValidationError.BlockFromFuture
import com.tacplatform.utx.UtxPoolImpl
import com.tacplatform.wallet.Wallet
import com.tacplatform.{TransactionGen, WithDB}
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.{FlatSpec, Matchers}

class MiningFailuresSuite extends FlatSpec with Matchers with PathMockFactory with WithDB with TransactionGen {
  trait BlockchainUpdaterNG extends Blockchain with BlockchainUpdater with NG

  behavior of "Miner"

  it should "generate valid blocks ignoring time errors " in {
    val blockchainUpdater = stub[BlockchainUpdaterNG]

    val tacSettings = {
      val config = ConfigFactory.parseString("""
          |tac.miner {
          |  quorum = 0
          |  interval-after-last-block-then-generation-is-allowed = 0
          |}
          |
          |tac.features.supported=[2]
          |""".stripMargin).withFallback(ConfigFactory.load())

      TacSettings.fromRootConfig(loadConfig(config))
    }

    val blockchainSettings = {
      val bs = tacSettings.blockchainSettings
      val fs = bs.functionalitySettings
      bs.copy(functionalitySettings = fs.copy(blockVersion3AfterHeight = 0, preActivatedFeatures = Map(2.toShort -> 0)))
    }

    val miner = {
      val scheduler   = Scheduler.singleThread("appender")
      val allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
      val wallet      = Wallet(WalletSettings(None, Some("123"), None))
      val utxPool     = new UtxPoolImpl(ntpTime, blockchainUpdater, ignoreSpendableBalanceChanged, tacSettings.utxSettings)
      val pos         = PoSSelector(blockchainUpdater, tacSettings.synchronizationSettings.maxBaseTargetOpt)
      new MinerImpl(
        allChannels,
        blockchainUpdater,
        tacSettings.copy(blockchainSettings = blockchainSettings),
        ntpTime,
        utxPool,
        wallet,
        pos,
        scheduler,
        scheduler
      )
    }

    val genesis = TestBlock.create(System.currentTimeMillis(), Nil)
    (blockchainUpdater.isLastBlockId _).when(genesis.id()).returning(true)
    (blockchainUpdater.heightOf _).when(genesis.id()).returning(Some(1)).anyNumberOfTimes()
    (blockchainUpdater.heightOf _).when(genesis.header.reference).returning(Some(1)).anyNumberOfTimes()
    (() => blockchainUpdater.height).when().returning(1)
    (() => blockchainUpdater.settings).when().returning(blockchainSettings)
    (blockchainUpdater.blockHeader _).when(*).returns(Some(SignedBlockHeader(genesis.header, genesis.signature)))
    (() => blockchainUpdater.activatedFeatures).when().returning(Map.empty)
    (() => blockchainUpdater.approvedFeatures).when().returning(Map.empty)
    (blockchainUpdater.hitSource _).when(*).returns(Some(ByteStr(new Array[Byte](32))))
    (blockchainUpdater.bestLastBlockInfo _)
      .when(*)
      .returning(
        Some(
          BlockMinerInfo(
            genesis.header.baseTarget,
            genesis.header.generationSignature,
            genesis.header.timestamp,
            genesis.id()
          )
        )
      )

    var minedBlock: Block = null
    (blockchainUpdater.processBlock _).when(*, *, *).returning(Left(BlockFromFuture(100))).repeated(10)
    (blockchainUpdater.processBlock _)
      .when(*, *, *)
      .onCall { (block, _, _) =>
        minedBlock = block
        Right(Nil)
      }
      .once()
    (blockchainUpdater.balanceSnapshots _).when(*, *, *).returning(Seq(BalanceSnapshot(1, ENOUGH_AMT, 0, 0)))

    val account       = accountGen.sample.get
    val generateBlock = generateBlockTask(miner)(account)
    generateBlock.runSyncUnsafe() shouldBe ((): Unit)
    minedBlock.header.featureVotes shouldBe empty
  }

  private[this] def generateBlockTask(miner: MinerImpl)(account: KeyPair): Task[Unit] =
    miner.generateBlockTask(account, None)
}
