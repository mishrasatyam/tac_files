package com.tacplatform.it.sync.grpc

import com.tacplatform.it.NTPTime
import com.tacplatform.it.sync._
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.protobuf.transaction.PBTransactions
import com.tacplatform.it.util._
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.protobuf.transaction.Recipient
import io.grpc.Status.Code

class ReissueTransactionGrpcSuite extends GrpcBaseTransactionSuite with NTPTime {

  val (reissuer, reissuerAddress) = (firstAcc, firstAddress)

  test("asset reissue changes issuer's asset balance; issuer's tac balance is decreased by fee") {
    for (v <- reissueTxSupportedVersions) {
      val reissuerBalance = sender.tacBalance(reissuerAddress).available
      val reissuerEffBalance = sender.tacBalance(reissuerAddress).effective

      val issuedAssetTx = sender.broadcastIssue(reissuer, "assetname", someAssetAmount, decimals = 2, reissuable = true, issueFee, waitForTx = true)
      val issuedAssetId = PBTransactions.vanilla(issuedAssetTx).explicitGet().id().toString

      sender.broadcastReissue(reissuer, reissueFee, issuedAssetId, someAssetAmount, reissuable = true, version = v, waitForTx = true)

      sender.tacBalance(reissuerAddress).available shouldBe reissuerBalance - issueFee - reissueFee
      sender.tacBalance(reissuerAddress).effective shouldBe reissuerEffBalance - issueFee - reissueFee
      sender.assetsBalance(reissuerAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 2 * someAssetAmount
    }
  }

  test("can't reissue not reissuable asset") {
    for (v <- reissueTxSupportedVersions) {
      val reissuerBalance = sender.tacBalance(reissuerAddress).available
      val reissuerEffBalance = sender.tacBalance(reissuerAddress).effective

      val issuedAssetTx = sender.broadcastIssue(reissuer, "assetname", someAssetAmount, decimals = 2, reissuable = false, issueFee, waitForTx = true)
      val issuedAssetId = PBTransactions.vanilla(issuedAssetTx).explicitGet().id().toString

      assertGrpcError(sender.broadcastReissue(reissuer, reissueFee, issuedAssetId, someAssetAmount, version = v, reissuable = true, waitForTx = true),
        "Asset is not reissuable",
        Code.INVALID_ARGUMENT)

      sender.tacBalance(reissuerAddress).available shouldBe reissuerBalance - issueFee
      sender.tacBalance(reissuerAddress).effective shouldBe reissuerEffBalance - issueFee
      sender.assetsBalance(reissuerAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe someAssetAmount
    }
  }

  test("not able to reissue if cannot pay fee - insufficient funds") {
    for (v <- reissueTxSupportedVersions) {
      val reissuerBalance = sender.tacBalance(reissuerAddress).available
      val reissuerEffBalance = sender.tacBalance(reissuerAddress).effective
      val hugeReissueFee = reissuerEffBalance + 1.tac

      val issuedAssetTx = sender.broadcastIssue(reissuer, "assetname", someAssetAmount, decimals = 2, reissuable = true, issueFee, waitForTx = true)
      val issuedAssetId = PBTransactions.vanilla(issuedAssetTx).explicitGet().id().toString

      assertGrpcError(sender.broadcastReissue(reissuer, hugeReissueFee, issuedAssetId, someAssetAmount, reissuable = true, version = v, waitForTx = true),
        "Accounts balance errors",
        Code.INVALID_ARGUMENT)

      sender.tacBalance(reissuerAddress).available shouldBe reissuerBalance - issueFee
      sender.tacBalance(reissuerAddress).effective shouldBe reissuerEffBalance - issueFee
      sender.assetsBalance(reissuerAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe someAssetAmount
    }
  }

  test("asset becomes non-reissuable after reissue with reissuable=false") {
    for (v <- reissueTxSupportedVersions) {
      val reissuerBalance = sender.tacBalance(reissuerAddress).available
      val reissuerEffBalance = sender.tacBalance(reissuerAddress).effective

      val issuedAssetTx = sender.broadcastIssue(reissuer, "assetname", someAssetAmount, decimals = 2, reissuable = true, issueFee, waitForTx = true)
      val issuedAssetId = PBTransactions.vanilla(issuedAssetTx).explicitGet().id().toString

      sender.broadcastReissue(reissuer, reissueFee, issuedAssetId, someAssetAmount, reissuable = false, version = v, waitForTx = true)

      assertGrpcError(sender.broadcastReissue(reissuer, reissueFee, issuedAssetId, someAssetAmount, reissuable = true, version = v, waitForTx = true),
        "Asset is not reissuable",
        Code.INVALID_ARGUMENT)

      sender.tacBalance(reissuerAddress).available shouldBe reissuerBalance - issueFee - reissueFee
      sender.tacBalance(reissuerAddress).effective shouldBe reissuerEffBalance - issueFee - reissueFee
      sender.assetsBalance(reissuerAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 2 * someAssetAmount
    }
  }

  test("able to transfer new reissued amount of assets") {
    for (v <- reissueTxSupportedVersions) {
      val reissuerBalance = sender.tacBalance(reissuerAddress).available
      val reissuerEffBalance = sender.tacBalance(reissuerAddress).effective

      val issuedAssetTx = sender.broadcastIssue(reissuer, "assetname", someAssetAmount, decimals = 2, reissuable = true, issueFee, waitForTx = true)
      val issuedAssetId = PBTransactions.vanilla(issuedAssetTx).explicitGet().id().toString

      sender.broadcastReissue(reissuer, reissueFee, issuedAssetId, someAssetAmount, reissuable = true, version = v, waitForTx = true)

      sender.broadcastTransfer(reissuer, Recipient().withPublicKeyHash(secondAddress), 2 * someAssetAmount, minFee, assetId = issuedAssetId, waitForTx = true)
      sender.tacBalance(reissuerAddress).available shouldBe reissuerBalance - issueFee - reissueFee - minFee
      sender.tacBalance(reissuerAddress).effective shouldBe reissuerEffBalance - issueFee - reissueFee - minFee
      sender.assetsBalance(reissuerAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 0L
      sender.assetsBalance(secondAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 2 * someAssetAmount
    }
  }


}
