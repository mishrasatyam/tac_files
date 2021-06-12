package com.tacplatform.it.sync.grpc

import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.it.sync._
import com.tacplatform.protobuf.transaction.{PBTransactions, Recipient}
import io.grpc.Status.Code

class BurnTransactionGrpcSuite extends GrpcBaseTransactionSuite {

  private val decimals: Byte = 2

  test("burning assets changes issuer's asset balance; issuer's tac balance is decreased by fee") {
    for (v <- burnTxSupportedVersions) {
      val issuedAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(firstAcc, s"name+$v", issueAmount, decimals, reissuable = false, fee = issueFee, waitForTx = true)
      ).explicitGet().id().toString

      sender.assetsBalance(firstAddress, Seq(issuedAssetId))(issuedAssetId) shouldBe issueAmount

      // burn half of the coins and check balance
      val balance = sender.tacBalance(firstAddress)
      sender.broadcastBurn(firstAcc, issuedAssetId, issueAmount / 2, minFee, version = v, waitForTx = true)

      sender.tacBalance(firstAddress).available shouldBe balance.available - minFee
      sender.tacBalance(firstAddress).effective shouldBe balance.effective - minFee

      sender.assetsBalance(firstAddress, Seq(issuedAssetId))(issuedAssetId) shouldBe issueAmount / 2

      // burn the rest and check again
      sender.broadcastBurn(firstAcc, issuedAssetId, issueAmount / 2, minFee, version = v, waitForTx = true)
      sender.assetsBalance(firstAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 0L
    }
  }

  test("non-issuer able to burn assets that they own") {
    for (v <- burnTxSupportedVersions) {
      val issuedQuantity      = issueAmount
      val transferredQuantity = issuedQuantity / 2

      val issuedAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(firstAcc, s"name+$v", issuedQuantity, decimals, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString
      sender.broadcastTransfer(firstAcc, Recipient().withPublicKeyHash(secondAddress), transferredQuantity, minFee, assetId = issuedAssetId, waitForTx = true)

      sender.broadcastBurn(secondAcc, issuedAssetId, transferredQuantity, minFee, version = v, waitForTx = true)
      sender.assetsBalance(secondAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 0L

      sender.waitForHeightArise()

      assertGrpcError(
        sender.broadcastTransfer(secondAcc, Recipient().withPublicKeyHash(firstAddress), transferredQuantity, minFee, assetId = issuedAssetId),
        "Attempt to transfer unavailable funds",
        Code.INVALID_ARGUMENT
      )
    }
  }

  test("issuer can't burn more tokens than they own") {
    for (v <- burnTxSupportedVersions) {
      val issuedQuantity = issueAmount
      val burnedQuantity = issuedQuantity + 1

      val issuedAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(firstAcc, s"name+$v", issuedQuantity, decimals, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString

      sender.waitForHeightArise()

      assertGrpcError(
        sender.broadcastBurn(firstAcc, issuedAssetId, burnedQuantity, minFee, version = v),
        "Accounts balance errors",
        Code.INVALID_ARGUMENT
      )
    }
  }

  test("user can't burn more tokens than they own") {
    for (v <- burnTxSupportedVersions) {
      val issuedQuantity      = issueAmount
      val transferredQuantity = issuedQuantity / 2
      val burnedQuantity      = transferredQuantity + 1

      val issuedAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(firstAcc, s"name+$v", issuedQuantity, decimals, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString
      sender.broadcastTransfer(firstAcc, Recipient().withPublicKeyHash(secondAddress), transferredQuantity, minFee, assetId = issuedAssetId, waitForTx = true)

      assertGrpcError(
        sender.broadcastBurn(secondAcc, issuedAssetId, burnedQuantity, minFee, version = v),
        "Accounts balance errors",
        Code.INVALID_ARGUMENT
      )
    }
  }

  test("non-owner can burn asset after reissue") {
    for (v <- burnTxSupportedVersions) {
      val issuedQuantity      = issueAmount
      val transferredQuantity = issuedQuantity / 2

      val issuedAssetId = PBTransactions.vanilla(
        sender.broadcastIssue(firstAcc, s"name+$v", issuedQuantity, decimals, reissuable = true, issueFee, waitForTx = true)
      ).explicitGet().id().toString

      sender.broadcastTransfer(firstAcc, Recipient().withPublicKeyHash(secondAddress), transferredQuantity, minFee, assetId = issuedAssetId, waitForTx = true)
      sender.broadcastBurn(firstAcc, issuedAssetId, transferredQuantity, minFee, v, waitForTx = true)

      sender.assetsBalance(firstAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 0L
      sender.assetsBalance(secondAddress, Seq(issuedAssetId))(issuedAssetId) shouldBe transferredQuantity

      sender.broadcastReissue(firstAcc, issueFee, issuedAssetId, issuedQuantity, waitForTx = true)

      sender.broadcastBurn(firstAcc, issuedAssetId, issuedQuantity, minFee, v, waitForTx = true)
      sender.broadcastBurn(secondAcc, issuedAssetId, transferredQuantity, minFee, v, waitForTx = true)

      sender.assetsBalance(firstAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 0L
      sender.assetsBalance(secondAddress, Seq(issuedAssetId)).getOrElse(issuedAssetId, 0L) shouldBe 0L
    }
  }

}
