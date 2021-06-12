package com.tacplatform.state

import com.typesafe.config.ConfigFactory
import com.tacplatform.TestHelpers.enableNG
import com.tacplatform.account.{Address, KeyPair}
import com.tacplatform.block.Block
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.DBCacheSettings
import com.tacplatform.events.BlockchainUpdateTriggers
import com.tacplatform.history.Domain.BlockchainUpdaterExt
import com.tacplatform.history.{chainBaseAndMicro, randomSig}
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.settings.{TestFunctionalitySettings, TacSettings, loadConfig}
import com.tacplatform.state.diffs.ENOUGH_AMT
import com.tacplatform.state.utils._
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{GenesisTransaction, Transaction}
import com.tacplatform.utils.Time
import com.tacplatform.{EitherMatchers, NTPTime, RequestGen, WithDB}
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FreeSpec, Matchers}

class BlockchainUpdaterImplSpec
    extends FreeSpec
    with Matchers
    with EitherMatchers
    with WithDB
    with RequestGen
    with NTPTime
    with DBCacheSettings
    with MockFactory {

  private val FEE_AMT = 1000000L

  // default settings, no NG
  private lazy val functionalitySettings = TestFunctionalitySettings.Stub
  private lazy val tacSettings         = TacSettings.fromRootConfig(loadConfig(ConfigFactory.load()))

  def baseTest(
      gen: Time => Gen[(KeyPair, Seq[Block])],
      enableNg: Boolean = false,
      triggers: BlockchainUpdateTriggers = BlockchainUpdateTriggers.noop
  )(
      f: (BlockchainUpdaterImpl, KeyPair) => Unit
  ): Unit = {
    val (fs, settings) =
      if (enableNg) (enableNG(functionalitySettings), enableNG(tacSettings)) else (functionalitySettings, tacSettings)

    val defaultWriter = TestLevelDB.withFunctionalitySettings(db, ignoreSpendableBalanceChanged, fs)
    val bcu           = new BlockchainUpdaterImpl(defaultWriter, ignoreSpendableBalanceChanged, settings, ntpTime, triggers, (_, _) => Seq.empty)
    try {
      val (account, blocks) = gen(ntpTime).sample.get

      blocks.foreach { block =>
        bcu.processBlock(block) should beRight
      }

      bcu.shutdown()
      f(bcu, account)
    } finally {
      bcu.shutdown()
      db.close()
    }
  }

  def createTransfer(master: KeyPair, recipient: Address, ts: Long): TransferTransaction = {
    TransferTransaction
      .selfSigned(1.toByte, master, recipient, Tac, ENOUGH_AMT / 5, Tac, 1000000, ByteStr.empty, ts)
      .explicitGet()
  }

  def commonPreconditions(ts: Long): Gen[(KeyPair, List[Block])] = {
    for {
      master    <- accountGen
      recipient <- accountGen
      genesisBlock = TestBlock
        .create(ts, Seq(GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()))
      b1 = TestBlock
        .create(
          ts + 10,
          genesisBlock.id(),
          Seq(
            createTransfer(master, recipient.toAddress, ts + 1),
            createTransfer(master, recipient.toAddress, ts + 2),
            createTransfer(recipient, master.toAddress, ts + 3),
            createTransfer(master, recipient.toAddress, ts + 4),
            createTransfer(master, recipient.toAddress, ts + 5)
          )
        )
      b2 = TestBlock.create(
        ts + 20,
        b1.id(),
        Seq(
          createTransfer(master, recipient.toAddress, ts + 11),
          createTransfer(recipient, master.toAddress, ts + 12),
          createTransfer(recipient, master.toAddress, ts + 13),
          createTransfer(recipient, master.toAddress, ts + 14)
        )
      )
    } yield (master, List(genesisBlock, b1, b2))
  }

  "blochain update events sending" - {
    "without NG" - {
      "genesis block and two transfers blocks" in {
        val triggersMock = mock[BlockchainUpdateTriggers]

        inSequence {
          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 0 &&
              block.transactionData.length == 1 &&
              diff.parentDiff.portfolios.head._2.balance == 0 &&
              diff.transactionDiffs.head.portfolios.head._2.balance == ENOUGH_AMT
            })
            .once()

          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 1 &&
              block.transactionData.length == 5 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 5 &&
              // first Tx updated balances
              diff.transactionDiffs.head.portfolios.head._2.balance == (ENOUGH_AMT / 5) &&
              diff.transactionDiffs.head.portfolios.last._2.balance == (-ENOUGH_AMT / 5 - FEE_AMT)
            })
            .once()

          (triggersMock.onProcessBlock _).expects(*, *, *, *).once()
        }

        baseTest(time => commonPreconditions(time.correctedTime()), enableNg = false, triggersMock)((_, _) => ())
      }
    }

    "with NG" - {
      "genesis block and two transfers blocks" in {
        val triggersMock = mock[BlockchainUpdateTriggers]

        inSequence {
          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 0 &&
              block.transactionData.length == 1 &&
              diff.parentDiff.portfolios.head._2.balance == 0 &&
              diff.transactionDiffs.head.portfolios.head._2.balance == ENOUGH_AMT
            })
            .once()

          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 1 &&
              block.transactionData.length == 5 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 5 * 0.4
            })
            .once()

          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 2 &&
              block.transactionData.length == 4 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == (
                FEE_AMT * 5 * 0.6     // carry from prev block
                  + FEE_AMT * 4 * 0.4 // current block reward
              )
            })
            .once()
        }

        baseTest(time => commonPreconditions(time.correctedTime()), enableNg = true, triggersMock)((_, _) => ())
      }

      "block, then 2 microblocks, then block referencing previous microblock" in {
        def preconditions(ts: Long): Gen[(Transaction, Seq[Transaction])] =
          for {
            master    <- accountGen
            recipient <- accountGen
            genesis = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
            transfers = Seq(
              createTransfer(master, recipient.toAddress, ts + 1),
              createTransfer(master, recipient.toAddress, ts + 2),
              createTransfer(master, recipient.toAddress, ts + 3),
              createTransfer(recipient, master.toAddress, ts + 4),
              createTransfer(master, recipient.toAddress, ts + 5)
            )
          } yield (genesis, transfers)

        val triggersMock = mock[BlockchainUpdateTriggers]

        val defaultWriter =
          TestLevelDB.withFunctionalitySettings(db, ignoreSpendableBalanceChanged, enableNG(functionalitySettings))
        val bcu =
          new BlockchainUpdaterImpl(defaultWriter, ignoreSpendableBalanceChanged, enableNG(tacSettings), ntpTime, triggersMock, (_, _) => Seq.empty)

        try {
          val (genesis, transfers)       = preconditions(0).sample.get
          val (block1, microBlocks1And2) = chainBaseAndMicro(randomSig, genesis, Seq(transfers.take(2), Seq(transfers(2))))
          val (block2, microBlock3)      = chainBaseAndMicro(microBlocks1And2.head.totalResBlockSig, transfers(3), Seq(Seq(transfers(4))))

          inSequence {
            // genesis
            (triggersMock.onProcessBlock _)
              .expects(where { (block, diff, _, bc) =>
                bc.height == 0 &&
                block.transactionData.length == 1 &&
                diff.parentDiff.portfolios.head._2.balance == 0 &&
                diff.transactionDiffs.head.portfolios.head._2.balance == ENOUGH_AMT
              })
              .once()

            // microblock 1
            (triggersMock.onProcessMicroBlock _)
              .expects(where { (microBlock, diff, bc, _, _) =>
                bc.height == 1 &&
                microBlock.transactionData.length == 2 &&
                // miner reward, no NG — all txs fees
                diff.parentDiff.portfolios.size == 1 &&
                diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 2 * 0.4
              })
              .once()

            // microblock 2
            (triggersMock.onProcessMicroBlock _)
              .expects(where { (microBlock, diff, bc, _, _) =>
                bc.height == 1 &&
                microBlock.transactionData.length == 1 &&
                // miner reward, no NG — all txs fees
                diff.parentDiff.portfolios.size == 1 &&
                diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 0.4
              })
              .once()

            // rollback microblock
            (triggersMock.onMicroBlockRollback _)
              .expects(where { (_, toSig) =>
                toSig == microBlocks1And2.head.totalResBlockSig
              })
              .once()

            // next keyblock
            (triggersMock.onProcessBlock _)
              .expects(where { (block, _, _, bc) =>
                bc.height == 1 &&
                block.header.reference == microBlocks1And2.head.totalResBlockSig
              })
              .once()

            // microblock 3
            (triggersMock.onProcessMicroBlock _)
              .expects(where { (microBlock, _, bc, _, _) =>
                bc.height == 2 && microBlock.reference == block2.signature
              })
              .once()
          }

          bcu.processBlock(block1) should beRight
          bcu.processMicroBlock(microBlocks1And2.head) should beRight
          bcu.processMicroBlock(microBlocks1And2.last) should beRight
          bcu.processBlock(block2) should beRight // this should remove previous microblock
          bcu.processMicroBlock(microBlock3.head) should beRight
          bcu.shutdown()
        } finally {
          bcu.shutdown()
          db.close()
        }
      }
    }
  }

}
