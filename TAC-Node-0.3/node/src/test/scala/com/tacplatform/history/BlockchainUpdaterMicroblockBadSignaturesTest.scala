package com.tacplatform.history

import com.tacplatform.{EitherMatchers, TransactionGen}
import com.tacplatform.account.KeyPair
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.crypto._
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.state.diffs._
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.transaction.transfer._
import com.tacplatform.history.Domain.BlockchainUpdaterExt
import org.scalacheck.Gen
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class BlockchainUpdaterMicroblockBadSignaturesTest
    extends PropSpec
    with PropertyChecks
    with DomainScenarioDrivenPropertyCheck
    with Matchers
    with EitherMatchers
    with TransactionGen {

  val preconditionsAndPayments: Gen[(GenesisTransaction, TransferTransaction, TransferTransaction)] = for {
    master    <- accountGen
    recipient <- accountGen
    ts        <- positiveIntGen
    genesis: GenesisTransaction = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
    payment: TransferTransaction  <- tacTransferGeneratorP(master, recipient.toAddress)
    payment2: TransferTransaction <- tacTransferGeneratorP(master, recipient.toAddress)
  } yield (genesis, payment, payment2)

  property("bad total resulting block signature") {
    assume(BlockchainFeatures.implemented.contains(BlockchainFeatures.SmartAccounts.id))
    scenario(preconditionsAndPayments) {
      case (domain, (genesis, payment, payment2)) =>
        val block0                 = buildBlockOfTxs(randomSig, Seq(genesis))
        val (block1, microblocks1) = chainBaseAndMicro(block0.id(), payment, Seq(payment2).map(Seq(_)))
        val badSigMicro            = microblocks1.head.copy(totalResBlockSig = randomSig)
        domain.blockchainUpdater.processBlock(block0) should beRight
        domain.blockchainUpdater.processBlock(block1) should beRight
        domain.blockchainUpdater.processMicroBlock(badSigMicro) should produce("InvalidSignature")
    }
  }

  property("bad microBlock signature") {
    assume(BlockchainFeatures.implemented.contains(BlockchainFeatures.SmartAccounts.id))
    scenario(preconditionsAndPayments) {
      case (domain, (genesis, payment, payment2)) =>
        val block0                 = buildBlockOfTxs(randomSig, Seq(genesis))
        val (block1, microblocks1) = chainBaseAndMicro(block0.id(), payment, Seq(payment2).map(Seq(_)))
        val badSigMicro            = microblocks1.head.copy(signature = randomSig)
        domain.blockchainUpdater.processBlock(block0) should beRight
        domain.blockchainUpdater.processBlock(block1) should beRight
        domain.blockchainUpdater.processMicroBlock(badSigMicro) should produce("InvalidSignature")
    }
  }

  property("other sender") {
    assume(BlockchainFeatures.implemented.contains(BlockchainFeatures.SmartAccounts.id))
    scenario(preconditionsAndPayments) {
      case (domain, (genesis, payment, payment2)) =>
        val otherSigner = KeyPair(TestBlock.randomOfLength(KeyLength))
        val block0      = buildBlockOfTxs(randomSig, Seq(genesis))
        val block1      = buildBlockOfTxs(block0.id(), Seq(payment))
        val badSigMicro = buildMicroBlockOfTxs(block0.id(), block1, Seq(payment2), otherSigner)._2
        domain.blockchainUpdater.processBlock(block0) should beRight
        domain.blockchainUpdater.processBlock(block1) should beRight
        domain.blockchainUpdater.processMicroBlock(badSigMicro) should produce("another account")
    }
  }
}
