package com.tacplatform.history

import com.tacplatform._
import com.tacplatform.block.{Block, MicroBlock}
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.state.diffs.ENOUGH_AMT
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.history.Domain.BlockchainUpdaterExt
import org.scalacheck.Gen
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class BlockchainUpdaterLiquidBlockTest
    extends PropSpec
    with PropertyChecks
    with DomainScenarioDrivenPropertyCheck
    with Matchers
    with EitherMatchers
    with TransactionGen
    with BlocksTransactionsHelpers
    with NoShrink {
  import QuickTX._
  import UnsafeBlocks._

  private def preconditionsAndPayments(minTx: Int, maxTx: Int): Gen[(Block, Block, Seq[MicroBlock])] =
    for {
      richAccount        <- accountGen
      totalTxNumber      <- Gen.chooseNum(minTx, maxTx)
      txNumberInKeyBlock <- Gen.chooseNum(0, Block.MaxTransactionsPerBlockVer3)
      allTxs             <- Gen.listOfN(totalTxNumber, transfer(richAccount, timestamp = Gen.delay(Gen.const(ntpTime.getTimestamp()))))
    } yield {
      val (keyBlockTxs, microTxs) = allTxs.splitAt(txNumberInKeyBlock)
      val txNumberInMicros        = totalTxNumber - txNumberInKeyBlock

      val prevBlock = unsafeBlock(
        reference = randomSig,
        txs = Seq(GenesisTransaction.create(richAccount.toAddress, ENOUGH_AMT, 0).explicitGet()),
        signer = TestBlock.defaultSigner,
        version = 3,
        timestamp = 0
      )

      val (keyBlock, microBlocks) = unsafeChainBaseAndMicro(
        totalRefTo = prevBlock.signature,
        base = keyBlockTxs,
        micros = microTxs.grouped(math.max(1, txNumberInMicros / 5)).toSeq,
        signer = TestBlock.defaultSigner,
        version = 3,
        timestamp = ntpNow
      )

      (prevBlock, keyBlock, microBlocks)
    }

  property("liquid block can't be overfilled") {
    import Block.{MaxTransactionsPerBlockVer3 => Max}
    forAll(preconditionsAndPayments(Max + 1, Max + 100)) {
      case (prevBlock, keyBlock, microBlocks) =>
        withDomain(MicroblocksActivatedAt0TacSettings) { d =>
          val blocksApplied = for {
            _ <- d.blockchainUpdater.processBlock(prevBlock)
            _ <- d.blockchainUpdater.processBlock(keyBlock)
          } yield ()

          val r = microBlocks.foldLeft(blocksApplied) {
            case (Right(_), curr) => d.blockchainUpdater.processMicroBlock(curr).map(_ => ())
            case (x, _)           => x
          }

          withClue("All microblocks should not be processed") {
            r match {
              case Left(e: GenericError) => e.err should include("Limit of txs was reached")
              case x =>
                val txNumberByMicroBlock = microBlocks.map(_.transactionData.size)
                fail(
                  s"Unexpected result: $x. keyblock txs: ${keyBlock.transactionData.length}, " +
                    s"microblock txs: ${txNumberByMicroBlock.mkString(", ")} (total: ${txNumberByMicroBlock.sum}), " +
                    s"total txs: ${keyBlock.transactionData.length + txNumberByMicroBlock.sum}"
                )
            }
          }
        }
    }
  }

  property("miner settings don't interfere with micro block processing") {
    val oneTxPerMicroSettings = MicroblocksActivatedAt0TacSettings
      .copy(
        minerSettings = MicroblocksActivatedAt0TacSettings.minerSettings.copy(
          maxTransactionsInMicroBlock = 1
        )
      )
    forAll(preconditionsAndPayments(10, Block.MaxTransactionsPerBlockVer3)) {
      case (genBlock, keyBlock, microBlocks) =>
        withDomain(oneTxPerMicroSettings) { d =>
          d.blockchainUpdater.processBlock(genBlock)
          d.blockchainUpdater.processBlock(keyBlock)
          microBlocks.foreach { mb =>
            d.blockchainUpdater.processMicroBlock(mb) should beRight
          }
        }
    }
  }
}
