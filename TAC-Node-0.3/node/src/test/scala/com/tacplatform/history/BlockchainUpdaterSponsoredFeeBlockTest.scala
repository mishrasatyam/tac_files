package com.tacplatform.history

import com.tacplatform.account.KeyPair
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.crypto._
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.history.Domain.BlockchainUpdaterExt
import com.tacplatform.settings.{BlockchainSettings, TacSettings}
import com.tacplatform.state._
import com.tacplatform.state.diffs._
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.assets.{IssueTransaction, SponsorFeeTransaction}
import com.tacplatform.transaction.transfer._
import com.tacplatform.transaction.{Asset, GenesisTransaction}
import com.tacplatform.{EitherMatchers, TransactionGen}
import org.scalacheck.Gen
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class BlockchainUpdaterSponsoredFeeBlockTest
    extends PropSpec
    with PropertyChecks
    with DomainScenarioDrivenPropertyCheck
    with Matchers
    with EitherMatchers
    with TransactionGen {

  private val amtTx = 100000

  type Setup =
    (GenesisTransaction, TransferTransaction, IssueTransaction, SponsorFeeTransaction, TransferTransaction, TransferTransaction, TransferTransaction)

  val sponsorPreconditions: Gen[Setup] = for {

    master                      <- accountGen
    ts                          <- timestampGen
    transferAssetTacFee       <- smallFeeGen
    _                           <- accountGen
    alice                       <- accountGen
    bob                         <- accountGen
    (feeAsset, sponsorTx, _, _) <- sponsorFeeCancelSponsorFeeGen(alice)
    tacFee                    = Sponsorship.toTac(sponsorTx.minSponsoredAssetFee.get, sponsorTx.minSponsoredAssetFee.get)
    genesis: GenesisTransaction = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
    masterToAlice: TransferTransaction = TransferTransaction
      .selfSigned(
        1.toByte,
        master,
        alice.toAddress,
        Tac,
        feeAsset.fee + sponsorTx.fee + transferAssetTacFee + tacFee,
        Tac,
        transferAssetTacFee,
        ByteStr.empty,
        ts + 1
      )
      .explicitGet()
    aliceToBob: TransferTransaction = TransferTransaction
      .selfSigned(
        1.toByte,
        alice,
        bob.toAddress,
        Asset.fromCompatId(Some(feeAsset.id())),
        feeAsset.quantity / 2,
        Tac,
        transferAssetTacFee,
        ByteStr.empty,
        ts + 2
      )
      .explicitGet()
    bobToMaster: TransferTransaction = TransferTransaction
      .selfSigned(
        1.toByte,
        bob,
        master.toAddress,
        Asset.fromCompatId(Some(feeAsset.id())),
        amtTx,
        Asset.fromCompatId(Some(feeAsset.id())),
        sponsorTx.minSponsoredAssetFee.get,
        ByteStr.empty,
        ts + 3
      )
      .explicitGet()
    bobToMaster2: TransferTransaction = TransferTransaction
      .selfSigned(
        1.toByte,
        bob,
        master.toAddress,
        Asset.fromCompatId(Some(feeAsset.id())),
        amtTx,
        Asset.fromCompatId(Some(feeAsset.id())),
        sponsorTx.minSponsoredAssetFee.get,
        ByteStr.empty,
        ts + 4
      )
      .explicitGet()
  } yield (genesis, masterToAlice, feeAsset, sponsorTx, aliceToBob, bobToMaster, bobToMaster2)

  val SponsoredFeeActivatedAt0BlockchainSettings: BlockchainSettings = DefaultBlockchainSettings.copy(
    functionalitySettings = DefaultBlockchainSettings.functionalitySettings
      .copy(
        featureCheckBlocksPeriod = 1,
        blocksForFeatureActivation = 1,
        preActivatedFeatures = Map(
          BlockchainFeatures.FeeSponsorship.id -> 0,
          BlockchainFeatures.NG.id             -> 0,
          BlockchainFeatures.BlockV5.id        -> 0
        )
      )
  )

  val SponsoredActivatedAt0TacSettings: TacSettings = settings.copy(blockchainSettings = SponsoredFeeActivatedAt0BlockchainSettings)

  property("not enough tac to sponsor sponsored tx") {
    scenario(sponsorPreconditions, SponsoredActivatedAt0TacSettings) {
      case (domain, (genesis, masterToAlice, feeAsset, sponsor, aliceToBob, bobToMaster, bobToMaster2)) =>
        val (block0, microBlocks) = chainBaseAndMicro(randomSig, genesis, Seq(masterToAlice, feeAsset, sponsor).map(Seq(_)))
        val block1 =
          customBuildBlockOfTxs(microBlocks.last.totalResBlockSig, Seq.empty, KeyPair(Array.fill(KeyLength)(1: Byte)), 3: Byte, sponsor.timestamp + 1)
        val block2 = customBuildBlockOfTxs(block1.id(), Seq.empty, KeyPair(Array.fill(KeyLength)(1: Byte)), 3: Byte, sponsor.timestamp + 1)
        val block3 = buildBlockOfTxs(block2.id(), Seq(aliceToBob, bobToMaster))
        val block4 = buildBlockOfTxs(block3.id(), Seq(bobToMaster2))

        domain.blockchainUpdater.processBlock(block0) should beRight
        domain.blockchainUpdater.processMicroBlock(microBlocks(0)) should beRight
        domain.blockchainUpdater.processMicroBlock(microBlocks(1)) should beRight
        domain.blockchainUpdater.processMicroBlock(microBlocks(2)) should beRight
        domain.blockchainUpdater.processBlock(block1) should beRight
        domain.blockchainUpdater.processBlock(block2) should beRight
        domain.blockchainUpdater.processBlock(block3) should beRight
        domain.blockchainUpdater.processBlock(block4) should produce("negative tac balance" /*"unavailable funds"*/ )
    }
  }

  property("calculates valid total fee for microblocks") {
    scenario(sponsorPreconditions, SponsoredActivatedAt0TacSettings) {
      case (domain, (genesis, masterToAlice, feeAsset, sponsor, aliceToBob, bobToMaster, _)) =>
        val (block0, microBlocks) = chainBaseAndMicro(randomSig, genesis, Seq(Seq(masterToAlice, feeAsset, sponsor), Seq(aliceToBob, bobToMaster)))

        val block0TotalFee = block0.transactionData
          .filter(_.assetFee._1 == Tac)
          .map(_.assetFee._2)
          .sum

        {
          domain.blockchainUpdater.processBlock(block0) should beRight
          domain.blockchainUpdater.bestLiquidDiffAndFees.map(_._3) should contain(block0TotalFee)
        }

        {
          domain.blockchainUpdater.processMicroBlock(microBlocks(0)) should beRight
          domain.blockchainUpdater.processMicroBlock(microBlocks(1)) should beRight

          val microBlocksTacFee = microBlocks
            .flatMap(_.transactionData)
            .map(tx => Sponsorship.calcTacFeeAmount(tx, ai => domain.blockchainUpdater.assetDescription(ai).map(_.sponsorship)))
            .sum

          domain.blockchainUpdater.bestLiquidDiffAndFees.map(_._3) should contain(block0TotalFee + microBlocksTacFee)
        }
    }
  }

}
