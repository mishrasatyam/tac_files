package com.tacplatform.it.sync.grpc

import com.google.protobuf.ByteString
import com.tacplatform.common.utils.{Base58, EitherExt2}
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.it.sync._
import com.tacplatform.protobuf.Amount
import com.tacplatform.protobuf.transaction.{PBTransactions, Recipient}
import com.tacplatform.state.diffs.FeeValidation
import io.grpc.Status.Code

class SponsorFeeTransactionGrpcSuite extends GrpcBaseTransactionSuite {
  val (alice, aliceAddress)     = (firstAcc, firstAddress)
  val (bob, bobAddress)         = (secondAcc, secondAddress)
  val (sponsor, sponsorAddress) = (thirdAcc, thirdAddress)
  val token             = 100L
  val sponsorAssetTotal = 100 * token
  val minSponsorFee     = token
  val tinyFee           = token / 2
  val smallFee          = token + token / 2
  val largeFee          = 10 * token
  val sponsorFeeTxSupportedVersions: List[Byte] = List(1, 2)

  test("able to make transfer with sponsored fee") {
    for (v <- sponsorFeeTxSupportedVersions) {
      val minerTacBalance = sender.tacBalance(ByteString.copyFrom(Base58.decode(miner.address)))
      val minerBalanceHeight = sender.height

      val sponsoredAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(sponsor, "SponsoredAsset", sponsorAssetTotal, 2, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString

      val sponsoredAssetMinFee = Some(Amount.of(ByteString.copyFrom(Base58.decode(sponsoredAssetId)), token))
      sender.broadcastSponsorFee(sponsor, sponsoredAssetMinFee, fee = sponsorReducedFee, version = v, waitForTx = true)

      sender.broadcastTransfer(sponsor, Recipient().withPublicKeyHash(aliceAddress), sponsorAssetTotal / 2, minFee, assetId = sponsoredAssetId, waitForTx = true)

      val aliceTacBalance = sender.tacBalance(aliceAddress)
      val bobTacBalance = sender.tacBalance(bobAddress)
      val sponsorTacBalance = sender.tacBalance(sponsorAddress)
      val aliceAssetBalance = sender.assetsBalance(aliceAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L)
      val bobAssetBalance = sender.assetsBalance(bobAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L)
      val sponsorAssetBalance = sender.assetsBalance(sponsorAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L)

      sender.broadcastTransfer(alice, Recipient().withPublicKeyHash(bobAddress), 10 * token, smallFee, assetId = sponsoredAssetId, feeAssetId = sponsoredAssetId, waitForTx = true)

      nodes.foreach(n => n.waitForHeight(n.height + 1))
      sender.tacBalance(aliceAddress).available shouldBe aliceTacBalance.available
      sender.tacBalance(bobAddress).available shouldBe bobTacBalance.available
      sender.tacBalance(sponsorAddress).available shouldBe sponsorTacBalance.available - FeeValidation.FeeUnit * smallFee / minSponsorFee
      sender.assetsBalance(aliceAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L) shouldBe aliceAssetBalance - 10 * token - smallFee
      sender.assetsBalance(bobAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L) shouldBe bobAssetBalance + 10 * token
      sender.assetsBalance(sponsorAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L) shouldBe sponsorAssetBalance + smallFee

      val reward = (sender.height - minerBalanceHeight) * 600000000L
      sender.tacBalance(ByteString.copyFrom(Base58.decode(miner.address))).available shouldBe
        minerTacBalance.available + reward + sponsorReducedFee + issueFee + minFee + FeeValidation.FeeUnit * smallFee / minSponsorFee
    }
  }

  test("only issuer is able to sponsor asset") {
    for (v <- sponsorFeeTxSupportedVersions) {
      val sponsoredAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(sponsor, "SponsoredAsset", sponsorAssetTotal, 2, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString

      val sponsoredAssetMinFee = Some(Amount.of(ByteString.copyFrom(Base58.decode(sponsoredAssetId)), token))
      assertGrpcError(
        sender.broadcastSponsorFee(alice, sponsoredAssetMinFee, fee = sponsorReducedFee, version = v),
        "Asset was issued by other address",
        Code.INVALID_ARGUMENT
      )
    }
  }

  test("sponsor is able to cancel sponsorship") {
    for (v <- sponsorFeeTxSupportedVersions) {
      val sponsoredAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(alice, "SponsoredAsset", sponsorAssetTotal, 2, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString

      val sponsoredAssetMinFee = Some(Amount.of(ByteString.copyFrom(Base58.decode(sponsoredAssetId)), token))
      sender.broadcastSponsorFee(alice, sponsoredAssetMinFee, fee = sponsorReducedFee, version = v, waitForTx = true)

      /**
        * Cancel sponsorship by sponsor None amount of sponsored asset.
        * As it is optional to pass all parameters to PB objects (Amount(assetId: ByteString, amount: Long) in this case),
        * we can simply pass unspecific (None) amount by creating Amount(assetId: ByteString). SponsorFeeTransaction with
        * that kind of Amount will cancel sponsorship.
        **/
      val sponsoredAssetNullMinFee = Some(Amount(ByteString.copyFrom(Base58.decode(sponsoredAssetId))))
      sender.broadcastSponsorFee(alice, sponsoredAssetNullMinFee, fee = sponsorReducedFee, version = v, waitForTx = true)

      assertGrpcError(
        sender.broadcastTransfer(alice, Recipient().withPublicKeyHash(bobAddress), 10 * token, smallFee, assetId = sponsoredAssetId, feeAssetId = sponsoredAssetId, waitForTx = true),
        s"Asset $sponsoredAssetId is not sponsored, cannot be used to pay fees",
        Code.INVALID_ARGUMENT
      )
    }
  }

  test("sponsor is able to update amount of sponsored fee") {
    for (v <- sponsorFeeTxSupportedVersions) {
      val sponsoredAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(sponsor, "SponsoredAsset", sponsorAssetTotal, 2, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString

      sender.broadcastTransfer(sponsor, Recipient().withPublicKeyHash(aliceAddress), sponsorAssetTotal / 2, minFee, assetId = sponsoredAssetId, waitForTx = true)

      val sponsoredAssetMinFee = Some(Amount.of(ByteString.copyFrom(Base58.decode(sponsoredAssetId)), token))
      sender.broadcastSponsorFee(sponsor, sponsoredAssetMinFee, fee = sponsorReducedFee, version = v, waitForTx = true)

      val sponsoredAssetUpdatedMinFee = Some(Amount(ByteString.copyFrom(Base58.decode(sponsoredAssetId)), largeFee))
      sender.broadcastSponsorFee(sponsor, sponsoredAssetUpdatedMinFee, fee = sponsorReducedFee, version = v, waitForTx = true)

      assertGrpcError(
        sender.broadcastTransfer(alice, Recipient().withPublicKeyHash(bobAddress), 10 * token, smallFee, assetId = sponsoredAssetId, feeAssetId = sponsoredAssetId, waitForTx = true),
        s"does not exceed minimal value of $minFee TAC or $largeFee $sponsoredAssetId",
        Code.INVALID_ARGUMENT
      )
      val aliceTacBalance = sender.tacBalance(aliceAddress)
      val bobTacBalance = sender.tacBalance(bobAddress)
      val sponsorTacBalance = sender.tacBalance(sponsorAddress)
      val aliceAssetBalance = sender.assetsBalance(aliceAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L)
      val bobAssetBalance = sender.assetsBalance(bobAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L)
      val sponsorAssetBalance = sender.assetsBalance(sponsorAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L)

      sender.broadcastTransfer(alice, Recipient().withPublicKeyHash(bobAddress), 10 * token, largeFee, assetId = sponsoredAssetId, feeAssetId = sponsoredAssetId, waitForTx = true)

      sender.tacBalance(aliceAddress).available shouldBe aliceTacBalance.available
      sender.tacBalance(bobAddress).available shouldBe bobTacBalance.available
      sender.tacBalance(sponsorAddress).available shouldBe sponsorTacBalance.available - FeeValidation.FeeUnit * largeFee / largeFee
      sender.assetsBalance(aliceAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L) shouldBe aliceAssetBalance - 10 * token - largeFee
      sender.assetsBalance(bobAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L) shouldBe bobAssetBalance + 10 * token
      sender.assetsBalance(sponsorAddress, Seq(sponsoredAssetId)).getOrElse(sponsoredAssetId, 0L) shouldBe sponsorAssetBalance + largeFee
    }
  }
}
