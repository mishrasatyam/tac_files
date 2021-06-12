package com.tacplatform.history

import com.tacplatform.{EitherMatchers, TransactionGen}
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.history.Domain.BlockchainUpdaterExt
import com.tacplatform.state.diffs._
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.transaction.transfer._
import org.scalacheck.Gen
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class BlockchainUpdaterGeneratorFeeNextBlockOrMicroBlockTest
    extends PropSpec
    with PropertyChecks
    with DomainScenarioDrivenPropertyCheck
    with Matchers
    with EitherMatchers
    with TransactionGen {

  type Setup = (GenesisTransaction, TransferTransaction, TransferTransaction, TransferTransaction)

  val preconditionsAndPayments: Gen[Setup] = for {
    sender    <- accountGen
    recipient <- accountGen
    ts        <- positiveIntGen
    genesis: GenesisTransaction      = GenesisTransaction.create(sender.toAddress, ENOUGH_AMT, ts).explicitGet()
    somePayment: TransferTransaction = createTacTransfer(sender, recipient.toAddress, 1, 10, ts + 1).explicitGet()
    // generator has enough balance for this transaction if gets fee for block before applying it
    generatorPaymentOnFee: TransferTransaction = createTacTransfer(defaultSigner, recipient.toAddress, 11, 1, ts + 2).explicitGet()
    someOtherPayment: TransferTransaction      = createTacTransfer(sender, recipient.toAddress, 1, 1, ts + 3).explicitGet()
  } yield (genesis, somePayment, generatorPaymentOnFee, someOtherPayment)

  property("generator should get fees before applying block before applyMinerFeeWithTransactionAfter in two blocks") {
    assume(BlockchainFeatures.implemented.contains(BlockchainFeatures.SmartAccounts.id))
    scenario(preconditionsAndPayments, DefaultTacSettings) {
      case (domain: Domain, (genesis, somePayment, generatorPaymentOnFee, someOtherPayment)) =>
        val blocks = chainBlocks(Seq(Seq(genesis, somePayment), Seq(generatorPaymentOnFee, someOtherPayment)))
        blocks.foreach(block => domain.blockchainUpdater.processBlock(block) should beRight)
    }
  }

  property("generator should get fees before applying block before applyMinerFeeWithTransactionAfter in block + micro") {
    scenario(preconditionsAndPayments, MicroblocksActivatedAt0TacSettings) {
      case (domain, (genesis, somePayment, generatorPaymentOnFee, someOtherPayment)) =>
        val (block, microBlocks) =
          chainBaseAndMicro(randomSig, genesis, Seq(Seq(somePayment), Seq(generatorPaymentOnFee, someOtherPayment)))
        domain.blockchainUpdater.processBlock(block) should beRight
        domain.blockchainUpdater.processMicroBlock(microBlocks.head) should beRight
        domain.blockchainUpdater.processMicroBlock(microBlocks(1)) should produce("unavailable funds")
    }
  }

  property("generator should get fees after applying every transaction after applyMinerFeeWithTransactionAfter in two blocks") {
    scenario(preconditionsAndPayments, MicroblocksActivatedAt0TacSettings) {
      case (domain, (genesis, somePayment, generatorPaymentOnFee, someOtherPayment)) =>
        val blocks = chainBlocks(Seq(Seq(genesis, somePayment), Seq(generatorPaymentOnFee, someOtherPayment)))
        domain.blockchainUpdater.processBlock(blocks.head) should beRight
        domain.blockchainUpdater.processBlock(blocks(1)) should produce("unavailable funds")
    }
  }

  property("generator should get fees after applying every transaction after applyMinerFeeWithTransactionAfter in block + micro") {
    scenario(preconditionsAndPayments, MicroblocksActivatedAt0TacSettings) {
      case (domain, (genesis, somePayment, generatorPaymentOnFee, someOtherPayment)) =>
        val (block, microBlocks) =
          chainBaseAndMicro(randomSig, genesis, Seq(Seq(somePayment), Seq(generatorPaymentOnFee, someOtherPayment)))
        domain.blockchainUpdater.processBlock(block) should beRight
        domain.blockchainUpdater.processMicroBlock(microBlocks.head) should beRight
        domain.blockchainUpdater.processMicroBlock(microBlocks(1)) should produce("unavailable funds")
    }
  }
}
