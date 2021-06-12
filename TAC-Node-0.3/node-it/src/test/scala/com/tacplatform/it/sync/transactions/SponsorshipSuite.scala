package com.tacplatform.it.sync.transactions

import com.typesafe.config.Config
import com.tacplatform.account.AddressScheme
import com.tacplatform.common.state.ByteStr
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.api.TransactionInfo
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.NodesFromDocker
import com.tacplatform.it.util._
import com.tacplatform.it.{IntegrationSuiteWithThreeAddresses, NodeConfigs, ReportingTestName}
import com.tacplatform.state.diffs.FeeValidation
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction.TxVersion
import com.tacplatform.transaction.assets.SponsorFeeTransaction
import org.scalatest.{Assertion, FreeSpec, Matchers}
import com.tacplatform.common.utils.EitherExt2

import scala.concurrent.duration._

class SponsorshipSuite
    extends FreeSpec
    with IntegrationSuiteWithThreeAddresses
    with NodesFromDocker
    with Matchers
    with ReportingTestName
    /*with CancelAfterFailure*/ {

  override def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .overrideBase(_.preactivatedFeatures((14, 1000000)))
      .overrideBase(_.raw("tac.blockchain.custom.functionality.blocks-for-feature-activation=1"))
      .overrideBase(_.raw("tac.blockchain.custom.functionality.feature-check-blocks-period=1"))
      .withDefault(1)
      .withSpecial(1, _.nonMiner)
      .buildNonConflicting()

  private def sponsor = firstKeyPair
  private def alice   = secondKeyPair
  private def bob     = thirdKeyPair

  private lazy val bobAddress = bob.toAddress.toString

  val Tac                                 = 100000000L
  val Token                                 = 100L
  val sponsorAssetTotal                     = 100 * Token
  val minSponsorFee                         = Token
  val TinyFee                               = Token / 2
  val SmallFee                              = Token + Token / 2
  val LargeFee                              = 10 * Token
  var sponsorTacBalance                   = 0L
  var minerTacBalance                     = 0L
  var minerTacBalanceAfterFirstXferTest   = 0L
  var sponsorTacBalanceAfterFirstXferTest = 0L
  var firstSponsorAssetId: String           = ""
  var secondSponsorAssetId: String          = ""
  var firstTransferTxToAlice: String        = ""
  var secondTransferTxToAlice: String       = ""
  var firstSponsorTxId: String              = ""
  var secondSponsorTxId: String             = ""

  def assertMinAssetFee(txId: String, sponsorship: Long): Assertion = {
    val txInfo = miner.transactionInfo[TransactionInfo](txId)
    assert(txInfo.minSponsoredAssetFee.contains(sponsorship))
  }

  def assertSponsorship(assetId: String, sponsorship: Long): Assertion = {
    val assetInfo = miner.assetsDetails(assetId)
    assert(assetInfo.minSponsoredAssetFee == Some(sponsorship).filter(_ != 0))
  }

  private lazy val aliceAddress: String   = alice.toAddress.toString
  private lazy val sponsorAddress: String = sponsor.toAddress.toString

  protected override def beforeAll(): Unit = {
    super.beforeAll()

    sponsorTacBalance = sender.accountBalances(sponsorAddress)._2
    minerTacBalance = sender.accountBalances(miner.address)._2
    minerTacBalanceAfterFirstXferTest = minerTacBalance + 2 * issueFee + 2 * sponsorReducedFee + 2 * minFee + 2 * FeeValidation.FeeUnit * SmallFee / minSponsorFee
    sponsorTacBalanceAfterFirstXferTest = sponsorTacBalance - 2 * issueFee - 2 * sponsorReducedFee - 2 * minFee - 2 * FeeValidation.FeeUnit * SmallFee / minSponsorFee

    firstSponsorAssetId = sender
      .issue(
        sponsor,
        "AssetTxV1",
        "Created by Sponsorship Suite",
        sponsorAssetTotal,
        decimals = 2,
        reissuable = false,
        fee = issueFee,
        waitForTx = true
      )
      .id
    secondSponsorAssetId = sender
      .issue(
        sponsor,
        "AssetTxV2",
        "Created by Sponsorship Suite",
        sponsorAssetTotal,
        decimals = 2,
        reissuable = false,
        fee = issueFee,
        waitForTx = true
      )
      .id

    firstTransferTxToAlice =
      sender.transfer(sponsor, aliceAddress, sponsorAssetTotal / 2, minFee, Some(firstSponsorAssetId), None, waitForTx = true).id
    secondTransferTxToAlice =
      sender.transfer(sponsor, aliceAddress, sponsorAssetTotal / 2, minFee, Some(secondSponsorAssetId), None, waitForTx = true).id
    firstSponsorTxId = sender.sponsorAsset(sponsor, firstSponsorAssetId, baseFee = Token, fee = sponsorReducedFee, version = TxVersion.V1).id
    secondSponsorTxId = sender.sponsorAsset(sponsor, secondSponsorAssetId, baseFee = Token, fee = sponsorReducedFee, version = TxVersion.V2).id
  }

  "Fee in sponsored asset works fine for transaction" - {

    "make assets sponsored" in {
      nodes.waitForHeightAriseAndTxPresent(firstSponsorTxId)
      nodes.waitForHeightAriseAndTxPresent(secondSponsorTxId)
      sender.transactionInfo[TransactionInfo](secondSponsorTxId).chainId shouldBe Some(AddressScheme.current.chainId)

      assertSponsorship(firstSponsorAssetId, 1 * Token)
      assertSponsorship(secondSponsorAssetId, 1 * Token)
      assertMinAssetFee(firstSponsorTxId, 1 * Token)
      assertMinAssetFee(secondSponsorTxId, 1 * Token)
    }

    "check balance before test accounts balances" in {
      for (sponsorAssetId <- Seq(firstSponsorAssetId, secondSponsorAssetId)) {
        sender.assertAssetBalance(sponsorAddress, sponsorAssetId, sponsorAssetTotal / 2)
        sender.assertAssetBalance(aliceAddress, sponsorAssetId, sponsorAssetTotal / 2)

        val assetInfo = sender.assetsBalance(aliceAddress).balances.filter(_.assetId == sponsorAssetId).head
        assetInfo.minSponsoredAssetFee shouldBe Some(Token)
        assetInfo.sponsorBalance shouldBe Some(sender.accountBalances(sponsorAddress)._2)
      }
    }

    "sender cannot make transfer" - {
      "invalid tx timestamp" in {
        for (v <- sponsorshipTxSupportedVersions) {
          def invalidTx(timestamp: Long): SponsorFeeTransaction =
            SponsorFeeTransaction
              .selfSigned(
                version = v,
                sponsor,
                IssuedAsset(ByteStr.decodeBase58(firstSponsorAssetId).get),
                Some(SmallFee),
                minFee,
                timestamp + 1.day.toMillis
              )
              .explicitGet()

          val iTx = invalidTx(timestamp = System.currentTimeMillis + 1.day.toMillis)
          assertBadRequestAndResponse(sender.broadcastRequest(iTx.json()), "Transaction timestamp .* is more than .*ms in the future")
        }
      }
    }

    "fee should be written off in issued asset" - {
      "alice transfer sponsored asset to bob using sponsored fee" in {
        val firstTransferTxCustomFeeAlice =
          sender.transfer(alice, bobAddress, 10 * Token, SmallFee, Some(firstSponsorAssetId), Some(firstSponsorAssetId)).id
        val secondTransferTxCustomFeeAlice =
          sender.transfer(alice, bobAddress, 10 * Token, SmallFee, Some(secondSponsorAssetId), Some(secondSponsorAssetId)).id
        nodes.waitForHeightArise()
        nodes.waitForTransaction(firstTransferTxCustomFeeAlice)
        nodes.waitForTransaction(secondTransferTxCustomFeeAlice)

        sender.assertAssetBalance(aliceAddress, firstSponsorAssetId, sponsorAssetTotal / 2 - SmallFee - 10 * Token)
        sender.assertAssetBalance(aliceAddress, secondSponsorAssetId, sponsorAssetTotal / 2 - SmallFee - 10 * Token)
        sender.assertAssetBalance(bobAddress, firstSponsorAssetId, 10 * Token)
        sender.assertAssetBalance(bobAddress, secondSponsorAssetId, 10 * Token)

        val aliceTxs = sender.transactionsByAddress(aliceAddress, 100)
        aliceTxs.size shouldBe 5 //not 4, because there was one more transaction in IntegrationSuiteWithThreeAddresses class
        aliceTxs.count(tx => tx.sender.contains(aliceAddress) || tx.recipient.contains(aliceAddress)) shouldBe 5
        aliceTxs.map(_.id) should contain allElementsOf Seq(
          firstTransferTxToAlice,
          secondTransferTxToAlice,
          firstTransferTxCustomFeeAlice,
          secondTransferTxCustomFeeAlice
        )

        val bobTxs = sender.transactionsByAddress(bobAddress, 100)
        bobTxs.size shouldBe 3
        bobTxs.count(tx => tx.sender.contains(bobAddress) || tx.recipient.contains(bobAddress)) shouldBe 3
        bobTxs.map(_.id) should contain allElementsOf Seq(firstTransferTxCustomFeeAlice, secondTransferTxCustomFeeAlice)
      }

      "check transactions by address" in {
        val minerTxs = sender.transactionsByAddress(miner.address, 100)
        minerTxs.size shouldBe 4

        val sponsorTxs = sender.transactionsByAddress(sponsorAddress, 100)
        sponsorTxs.size shouldBe 9 //TODO: bug?
        sponsorTxs.count(tx => tx.sender.contains(sponsorAddress) || tx.recipient.contains(sponsorAddress)) shouldBe 7
        sponsorTxs.map(_.id) should contain allElementsOf Seq(
          firstSponsorAssetId,
          secondSponsorAssetId,
          firstTransferTxToAlice,
          secondTransferTxToAlice,
          firstSponsorTxId,
          secondSponsorTxId
        )
      }

      "sponsor should receive sponsored asset as fee, tac should be written off" in {
        miner.assertAssetBalance(sponsorAddress, firstSponsorAssetId, sponsorAssetTotal / 2 + SmallFee)
        miner.assertAssetBalance(sponsorAddress, secondSponsorAssetId, sponsorAssetTotal / 2 + SmallFee)
        miner.assertBalances(sponsorAddress, sponsorTacBalanceAfterFirstXferTest)
      }

      "miner tac balance should be changed" in {
        miner.assertBalances(miner.address, minerTacBalanceAfterFirstXferTest)
      }
    }

    "assets balance should contain sponsor fee info and sponsor balance" in {
      val sponsorLeaseSomeTac = sender.lease(sponsor, bobAddress, leasingAmount, leasingFee).id
      nodes.waitForHeightAriseAndTxPresent(sponsorLeaseSomeTac)
      val (_, sponsorEffectiveBalance)   = sender.accountBalances(sponsorAddress)
      val aliceFirstSponsorAssetBalance  = sender.assetsBalance(aliceAddress).balances.filter(_.assetId == firstSponsorAssetId).head
      val aliceSecondSponsorAssetBalance = sender.assetsBalance(aliceAddress).balances.filter(_.assetId == secondSponsorAssetId).head
      aliceFirstSponsorAssetBalance.minSponsoredAssetFee shouldBe Some(minSponsorFee)
      aliceSecondSponsorAssetBalance.minSponsoredAssetFee shouldBe Some(minSponsorFee)
      aliceFirstSponsorAssetBalance.sponsorBalance shouldBe Some(sponsorEffectiveBalance)
      aliceSecondSponsorAssetBalance.sponsorBalance shouldBe Some(sponsorEffectiveBalance)
    }

    "tac fee depends on sponsor fee and sponsored token decimals" in {
      val transferTxCustomLargeFeeAlice1 = sender.transfer(alice, bobAddress, 1.tac, LargeFee, None, Some(firstSponsorAssetId)).id
      val transferTxCustomLargeFeeAlice2 = sender.transfer(alice, bobAddress, 1.tac, LargeFee, None, Some(secondSponsorAssetId)).id
      nodes.waitForHeightAriseAndTxPresent(transferTxCustomLargeFeeAlice1)
      nodes.waitForHeightAriseAndTxPresent(transferTxCustomLargeFeeAlice2)

      sender.assertAssetBalance(sponsorAddress, firstSponsorAssetId, sponsorAssetTotal / 2 + SmallFee + LargeFee)
      sender.assertAssetBalance(sponsorAddress, secondSponsorAssetId, sponsorAssetTotal / 2 + SmallFee + LargeFee)
      sender.assertAssetBalance(aliceAddress, firstSponsorAssetId, sponsorAssetTotal / 2 - SmallFee - LargeFee - 10 * Token)
      sender.assertAssetBalance(aliceAddress, secondSponsorAssetId, sponsorAssetTotal / 2 - SmallFee - LargeFee - 10 * Token)
      sender.assertAssetBalance(bobAddress, firstSponsorAssetId, 10 * Token)
      sender.assertAssetBalance(bobAddress, secondSponsorAssetId, 10 * Token)
      sender.assertBalances(
        sponsorAddress,
        sponsorTacBalanceAfterFirstXferTest - FeeValidation.FeeUnit * 2 * LargeFee / Token - leasingFee,
        sponsorTacBalanceAfterFirstXferTest - FeeValidation.FeeUnit * 2 * LargeFee / Token - leasingFee - leasingAmount
      )
      miner.assertBalances(miner.address, minerTacBalanceAfterFirstXferTest + FeeValidation.FeeUnit * 2 * LargeFee / Token + leasingFee)
    }

    "cancel sponsorship" - {

      "cancel" in {
        val cancelFirstSponsorTxId  = sender.cancelSponsorship(sponsor, firstSponsorAssetId, fee = issueFee, version = TxVersion.V1).id
        val cancelSecondSponsorTxId = sender.cancelSponsorship(sponsor, secondSponsorAssetId, fee = issueFee, version = TxVersion.V2).id
        nodes.waitForHeightAriseAndTxPresent(cancelFirstSponsorTxId)
        nodes.waitForHeightAriseAndTxPresent(cancelSecondSponsorTxId)
      }

      "check asset details info" in {
        for (sponsorAssetId <- Seq(firstSponsorAssetId, secondSponsorAssetId)) {
          val assetInfo = sender.assetsBalance(aliceAddress).balances.filter(_.assetId == sponsorAssetId).head
          assetInfo.minSponsoredAssetFee shouldBe None
          assetInfo.sponsorBalance shouldBe None
        }
      }

      "cannot pay fees in non sponsored assets" in {
        assertBadRequestAndResponse(
          sender.transfer(alice, bobAddress, 10 * Token, fee = 1 * Token, assetId = None, feeAssetId = Some(firstSponsorAssetId)).id,
          s"Asset $firstSponsorAssetId is not sponsored, cannot be used to pay fees"
        )
        assertBadRequestAndResponse(
          sender.transfer(alice, bobAddress, 10 * Token, fee = 1 * Token, assetId = None, feeAssetId = Some(secondSponsorAssetId)).id,
          s"Asset $secondSponsorAssetId is not sponsored, cannot be used to pay fees"
        )
      }

      "check cancel transaction info" in {
        assertSponsorship(firstSponsorAssetId, 0L)
        assertSponsorship(secondSponsorAssetId, 0L)
      }

      "check sponsor and miner balances after cancel" in {
        sender.assertBalances(
          sponsorAddress,
          sponsorTacBalanceAfterFirstXferTest - FeeValidation.FeeUnit * 2 * LargeFee / Token - leasingFee - 2 * issueFee,
          sponsorTacBalanceAfterFirstXferTest - FeeValidation.FeeUnit * 2 * LargeFee / Token - leasingFee - leasingAmount - 2 * issueFee
        )
        miner.assertBalances(
          miner.address,
          minerTacBalanceAfterFirstXferTest + FeeValidation.FeeUnit * 2 * LargeFee / Token + leasingFee + 2 * issueFee
        )
      }

      "cancel sponsorship again" in {
        val cancelSponsorshipTxId1 = sender.cancelSponsorship(sponsor, firstSponsorAssetId, fee = issueFee, version = TxVersion.V1).id
        val cancelSponsorshipTxId2 = sender.cancelSponsorship(sponsor, firstSponsorAssetId, fee = issueFee, version = TxVersion.V2).id
        nodes.waitForHeightArise()
        nodes.waitForTransaction(cancelSponsorshipTxId1)
        nodes.waitForTransaction(cancelSponsorshipTxId2)
      }

    }
    "set sponsopship again" - {

      "set sponsorship and check new asset details, min sponsored fee changed" in {
        val setAssetSponsoredTx1 = sender.sponsorAsset(sponsor, firstSponsorAssetId, fee = issueFee, baseFee = TinyFee, version = TxVersion.V1).id
        val setAssetSponsoredTx2 = sender.sponsorAsset(sponsor, secondSponsorAssetId, fee = issueFee, baseFee = TinyFee, version = TxVersion.V2).id
        nodes.waitForHeightAriseAndTxPresent(setAssetSponsoredTx1)
        nodes.waitForHeightAriseAndTxPresent(setAssetSponsoredTx2)
        for (sponsorAssetId <- Seq(firstSponsorAssetId, secondSponsorAssetId)) {
          val assetInfo = sender.assetsBalance(aliceAddress).balances.filter(_.assetId == sponsorAssetId).head
          assetInfo.minSponsoredAssetFee shouldBe Some(Token / 2)
          assetInfo.sponsorBalance shouldBe Some(sender.accountBalances(sponsorAddress)._2)
        }
      }

      "make transfer with new min sponsored fee" in {
        val sponsoredBalance          = sender.accountBalances(sponsorAddress)
        val sponsorFirstAssetBalance  = sender.assetBalance(sponsorAddress, firstSponsorAssetId).balance
        val sponsorSecondAssetBalance = sender.assetBalance(sponsorAddress, secondSponsorAssetId).balance
        val aliceFirstAssetBalance    = sender.assetBalance(aliceAddress, firstSponsorAssetId).balance
        val aliceSecondAssetBalance   = sender.assetBalance(aliceAddress, secondSponsorAssetId).balance
        val aliceTacBalance         = sender.accountBalances(aliceAddress)
        val bobFirstAssetBalance      = sender.assetBalance(bobAddress, firstSponsorAssetId).balance
        val bobSecondAssetBalance     = sender.assetBalance(bobAddress, secondSponsorAssetId).balance
        val bobTacBalance           = sender.accountBalances(bobAddress)
        val minerBalance              = miner.accountBalances(miner.address)
        val minerFirstAssetBalance    = miner.assetBalance(miner.address, firstSponsorAssetId).balance
        val minerSecondAssetBalance   = miner.assetBalance(miner.address, secondSponsorAssetId).balance

        val transferTxCustomFeeAlice1 = sender.transfer(alice, bobAddress, 1.tac, TinyFee, None, Some(firstSponsorAssetId)).id
        val transferTxCustomFeeAlice2 = sender.transfer(alice, bobAddress, 1.tac, TinyFee, None, Some(secondSponsorAssetId)).id
        nodes.waitForHeight(
          math.max(
            sender.waitForTransaction(transferTxCustomFeeAlice1).height,
            sender.waitForTransaction(transferTxCustomFeeAlice2).height
          ) + 2
        )

        val tacFee = FeeValidation.FeeUnit * 2 * TinyFee / TinyFee
        sender.assertBalances(sponsorAddress, sponsoredBalance._1 - tacFee, sponsoredBalance._2 - tacFee)
        sender.assertAssetBalance(sponsorAddress, firstSponsorAssetId, sponsorFirstAssetBalance + TinyFee)
        sender.assertAssetBalance(sponsorAddress, secondSponsorAssetId, sponsorSecondAssetBalance + TinyFee)
        sender.assertAssetBalance(aliceAddress, firstSponsorAssetId, aliceFirstAssetBalance - TinyFee)
        sender.assertAssetBalance(aliceAddress, secondSponsorAssetId, aliceSecondAssetBalance - TinyFee)
        sender.assertBalances(aliceAddress, aliceTacBalance._1 - 2.tac, aliceTacBalance._2 - 2.tac)
        sender.assertBalances(bobAddress, bobTacBalance._1 + 2.tac, bobTacBalance._2 + 2.tac)
        sender.assertAssetBalance(bobAddress, firstSponsorAssetId, bobFirstAssetBalance)
        sender.assertAssetBalance(bobAddress, secondSponsorAssetId, bobSecondAssetBalance)
        miner.assertBalances(miner.address, minerBalance._2 + tacFee)
        miner.assertAssetBalance(miner.address, firstSponsorAssetId, minerFirstAssetBalance)
        miner.assertAssetBalance(miner.address, secondSponsorAssetId, minerSecondAssetBalance)
      }

      "change sponsorship fee in active sponsored asset" in {
        val setAssetSponsoredTx1 = sender.sponsorAsset(sponsor, firstSponsorAssetId, fee = issueFee, baseFee = LargeFee, version = TxVersion.V1).id
        val setAssetSponsoredTx2 = sender.sponsorAsset(sponsor, secondSponsorAssetId, fee = issueFee, baseFee = LargeFee, version = TxVersion.V2).id
        nodes.waitForHeightArise()
        nodes.waitForHeightAriseAndTxPresent(setAssetSponsoredTx1)
        nodes.waitForTransaction(setAssetSponsoredTx2)

        for (sponsorAssetId <- Seq(firstSponsorAssetId, secondSponsorAssetId)) {
          val assetInfo = sender.assetsBalance(aliceAddress).balances.filter(_.assetId == sponsorAssetId).head
          assetInfo.minSponsoredAssetFee shouldBe Some(LargeFee)
          assetInfo.sponsorBalance shouldBe Some(sender.accountBalances(sponsorAddress)._2)
        }
      }

      "transfer tx sponsored fee is less then new minimal" in {
        assertBadRequestAndResponse(
          sender
            .transfer(sponsor, aliceAddress, 11 * Token, fee = SmallFee, assetId = Some(firstSponsorAssetId), feeAssetId = Some(firstSponsorAssetId))
            .id,
          s"Fee for TransferTransaction \\($SmallFee in ${Some(firstSponsorAssetId).get}\\) does not exceed minimal value of 100000 TAC or $LargeFee ${Some(firstSponsorAssetId).get}"
        )
        assertBadRequestAndResponse(
          sender
            .transfer(
              sponsor,
              aliceAddress,
              11 * Token,
              fee = SmallFee,
              assetId = Some(secondSponsorAssetId),
              feeAssetId = Some(secondSponsorAssetId)
            )
            .id,
          s"Fee for TransferTransaction \\($SmallFee in ${Some(secondSponsorAssetId).get}\\) does not exceed minimal value of 100000 TAC or $LargeFee ${Some(secondSponsorAssetId).get}"
        )
      }

      "make transfer with updated min sponsored fee" in {
        val sponsoredBalance          = sender.accountBalances(sponsorAddress)
        val sponsorFirstAssetBalance  = sender.assetBalance(sponsorAddress, firstSponsorAssetId).balance
        val sponsorSecondAssetBalance = sender.assetBalance(sponsorAddress, secondSponsorAssetId).balance
        val aliceFirstAssetBalance    = sender.assetBalance(aliceAddress, firstSponsorAssetId).balance
        val aliceSecondAssetBalance   = sender.assetBalance(aliceAddress, firstSponsorAssetId).balance
        val aliceTacBalance         = sender.accountBalances(aliceAddress)
        val bobTacBalance           = sender.accountBalances(bobAddress)
        val minerBalance              = miner.accountBalances(miner.address)

        val transferTxCustomFeeAlice1 = sender.transfer(alice, bobAddress, 1.tac, LargeFee, None, Some(firstSponsorAssetId)).id
        val transferTxCustomFeeAlice2 = sender.transfer(alice, bobAddress, 1.tac, LargeFee, None, Some(secondSponsorAssetId)).id
        nodes.waitForHeightArise()
        nodes.waitForTransaction(transferTxCustomFeeAlice1)
        nodes.waitForTransaction(transferTxCustomFeeAlice2)
        val tacFee = FeeValidation.FeeUnit * 2 * LargeFee / LargeFee
        nodes.waitForHeightArise()

        sender.assertBalances(sponsorAddress, sponsoredBalance._1 - tacFee, sponsoredBalance._2 - tacFee)
        sender.assertAssetBalance(sponsorAddress, firstSponsorAssetId, sponsorFirstAssetBalance + LargeFee)
        sender.assertAssetBalance(sponsorAddress, secondSponsorAssetId, sponsorSecondAssetBalance + LargeFee)
        sender.assertAssetBalance(aliceAddress, firstSponsorAssetId, aliceFirstAssetBalance - LargeFee)
        sender.assertAssetBalance(aliceAddress, secondSponsorAssetId, aliceSecondAssetBalance - LargeFee)

        sender.assertBalances(aliceAddress, aliceTacBalance._1 - 2.tac, aliceTacBalance._2 - 2.tac)
        sender.assertBalances(bobAddress, bobTacBalance._1 + 2.tac, bobTacBalance._2 + 2.tac)
        miner.assertBalances(miner.address, minerBalance._1 + tacFee, minerBalance._2 + tacFee)
      }

    }

    "issue asset make sponsor and burn and reissue" in {
      val sponsorBalance = sender.accountBalances(sponsorAddress)
      val minerBalance   = miner.accountBalances(miner.address)

      val firstSponsorAssetId2 =
        sender
          .issue(
            sponsor,
            "Another1",
            "Created by Sponsorship Suite",
            sponsorAssetTotal,
            decimals = 2,
            fee = issueFee,
            waitForTx = true
          )
          .id
      val secondSponsorAssetId2 =
        sender
          .issue(
            sponsor,
            "Another2",
            "Created by Sponsorship Suite",
            sponsorAssetTotal,
            decimals = 2,
            reissuable = true,
            fee = issueFee,
            waitForTx = true
          )
          .id
      val sponsorTxId1 = sender.sponsorAsset(sponsor, firstSponsorAssetId2, baseFee = Token, fee = sponsorReducedFee, version = TxVersion.V1).id
      val sponsorTxId2 = sender.sponsorAsset(sponsor, secondSponsorAssetId2, baseFee = Token, fee = sponsorReducedFee, version = TxVersion.V2).id
      sender.transfer(sponsor, aliceAddress, sponsorAssetTotal / 2, minFee, Some(firstSponsorAssetId2), None, waitForTx = true).id
      sender.transfer(sponsor, aliceAddress, sponsorAssetTotal / 2, minFee, Some(secondSponsorAssetId2), None, waitForTx = true).id
      nodes.waitForHeightAriseAndTxPresent(sponsorTxId1)
      nodes.waitForTransaction(sponsorTxId2)

      sender.burn(sponsor, firstSponsorAssetId2, sponsorAssetTotal / 2, burnFee, waitForTx = true).id
      sender.burn(sponsor, secondSponsorAssetId2, sponsorAssetTotal / 2, burnFee, waitForTx = true).id

      for (sponsorAssetId2 <- Seq(firstSponsorAssetId2, secondSponsorAssetId2)) {
        val assetInfo = sender.assetsDetails(sponsorAssetId2)
        assetInfo.minSponsoredAssetFee shouldBe Some(Token)
        assetInfo.quantity shouldBe sponsorAssetTotal / 2
      }

      sender.reissue(sponsor, firstSponsorAssetId2, sponsorAssetTotal, reissuable = true, issueFee, waitForTx = true).id
      sender.reissue(sponsor, secondSponsorAssetId2, sponsorAssetTotal, reissuable = true, issueFee, waitForTx = true).id

      for (sponsorAssetId2 <- Seq(firstSponsorAssetId2, secondSponsorAssetId2)) {
        val assetInfoAfterReissue = sender.assetsDetails(sponsorAssetId2)
        assetInfoAfterReissue.minSponsoredAssetFee shouldBe Some(Token)
        assetInfoAfterReissue.quantity shouldBe sponsorAssetTotal / 2 + sponsorAssetTotal
        assetInfoAfterReissue.reissuable shouldBe true
      }

      val aliceTransferTac1 = sender.transfer(alice, bobAddress, transferAmount, SmallFee, None, Some(firstSponsorAssetId2), waitForTx = true).id
      val aliceTransferTac2 = sender.transfer(alice, bobAddress, transferAmount, SmallFee, None, Some(secondSponsorAssetId2), waitForTx = true).id
      nodes.waitForHeightAriseAndTxPresent(aliceTransferTac1)
      nodes.waitForHeightAriseAndTxPresent(aliceTransferTac2)

      val totalTacFee = FeeValidation.FeeUnit * 2 * SmallFee / Token + 2 * issueFee + 2 * sponsorReducedFee + 2 * burnFee + 2 * minFee + 2 * issueFee
      miner.assertBalances(miner.address, minerBalance._1 + totalTacFee, minerBalance._2 + totalTacFee)
      sender.assertBalances(sponsorAddress, sponsorBalance._1 - totalTacFee, sponsorBalance._2 - totalTacFee)
      sender.assertAssetBalance(sponsorAddress, firstSponsorAssetId2, SmallFee + sponsorAssetTotal)
      sender.assertAssetBalance(sponsorAddress, secondSponsorAssetId2, SmallFee + sponsorAssetTotal)
    }

    "miner is sponsor" in {
      val minerBalance = miner.accountBalances(miner.address)
      val firstMinersAsset =
        miner
          .issue(
            miner.keyPair,
            "MinersAsset1",
            "Created by Sponsorship Suite",
            sponsorAssetTotal,
            decimals = 8,
            fee = issueFee,
            waitForTx = true
          )
          .id
      val secondMinersAsset =
        miner
          .issue(
            miner.keyPair,
            "MinersAsset2",
            "Created by Sponsorship Suite",
            sponsorAssetTotal,
            decimals = 8,
            reissuable = true,
            fee = issueFee,
            waitForTx = true
          )
          .id
      val firstSponsorshipTxId =
        miner.sponsorAsset(miner.keyPair, firstMinersAsset, baseFee = Token, fee = sponsorReducedFee, version = TxVersion.V1).id
      val secondSponsorshipTxId =
        miner.sponsorAsset(miner.keyPair, secondMinersAsset, baseFee = Token, fee = sponsorReducedFee, version = TxVersion.V2).id
      nodes.waitForHeightAriseAndTxPresent(firstSponsorshipTxId)
      nodes.waitForTransaction(secondSponsorshipTxId)
      val minerFirstTransferTxId =
        miner.transfer(miner.keyPair, aliceAddress, sponsorAssetTotal / 2, SmallFee, Some(firstMinersAsset), Some(firstMinersAsset)).id
      val minerSecondTransferTxId =
        miner.transfer(miner.keyPair, aliceAddress, sponsorAssetTotal / 2, SmallFee, Some(secondMinersAsset), Some(secondMinersAsset)).id
      nodes.waitForHeightAriseAndTxPresent(minerFirstTransferTxId)
      nodes.waitForHeightAriseAndTxPresent(minerSecondTransferTxId)

      miner.assertBalances(miner.address, minerBalance._1)
      val aliceFirstTransferTacId  = sender.transfer(alice, bobAddress, transferAmount, SmallFee, None, Some(firstMinersAsset)).id
      val aliceSecondTransferTacId = sender.transfer(alice, bobAddress, transferAmount, SmallFee, None, Some(secondMinersAsset)).id
      nodes.waitForHeightAriseAndTxPresent(aliceFirstTransferTacId)
      nodes.waitForHeightAriseAndTxPresent(aliceSecondTransferTacId)

      miner.assertBalances(miner.address, minerBalance._1)
      miner.assertAssetBalance(miner.address, firstMinersAsset, sponsorAssetTotal / 2 + SmallFee)
      miner.assertAssetBalance(miner.address, secondMinersAsset, sponsorAssetTotal / 2 + SmallFee)
    }

    "tx is declined if sponsor has not enough effective balance to pay fee" in {
      val sponsorEffectiveBalance = sender.accountBalances(sponsorAddress)._2
      sender.lease(sponsor, bobAddress, sponsorEffectiveBalance - leasingFee, leasingFee, waitForTx = true).id
      assertBadRequestAndMessage(
        sender.transfer(alice, bobAddress, 10 * Token, LargeFee, Some(firstSponsorAssetId), Some(firstSponsorAssetId)),
        "unavailable funds"
      )
      assertBadRequestAndMessage(
        sender.transfer(alice, bobAddress, 10 * Token, LargeFee, Some(secondSponsorAssetId), Some(secondSponsorAssetId)),
        "unavailable funds"
      )
    }
  }
}
