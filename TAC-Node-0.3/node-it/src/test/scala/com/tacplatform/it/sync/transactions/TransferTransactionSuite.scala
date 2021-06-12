package com.tacplatform.it.sync.transactions

import com.tacplatform.account.{AddressOrAlias, AddressScheme}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.api.TransferTransactionInfo
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.it.util._
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.transfer._
import org.scalatest.CancelAfterFailure
import play.api.libs.json.Json

import scala.concurrent.duration._

class TransferTransactionSuite extends BaseTransactionSuite with CancelAfterFailure {
  test("transfer with empty string assetId") {
    val tx = TransferTransaction.selfSigned(2.toByte, sender.keyPair, sender.keyPair.toAddress, Tac, 100L, Tac, minFee, ByteStr.empty, System.currentTimeMillis()).explicitGet()
    val json = tx.json() ++ Json.obj("assetId" -> "", "feeAssetId" -> "")
    sender.signedBroadcast(json, waitForTx = true)
  }

  test("asset transfer changes sender's and recipient's asset balance; issuer's.tac balance is decreased by fee") {
    for (v <- transferTxSupportedVersions) {
      val (firstBalance, firstEffBalance)   = miner.accountBalances(firstAddress)
      val (secondBalance, secondEffBalance) = miner.accountBalances(secondAddress)

      val issuedAssetId = sender.issue(firstKeyPair, "name", "description", someAssetAmount, 2, reissuable = false, issueFee).id

      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

      miner.assertBalances(firstAddress, firstBalance - issueFee, firstEffBalance - issueFee)
      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)

      val transferTransaction = sender.transfer(firstKeyPair, secondAddress, someAssetAmount, minFee, Some(issuedAssetId), version = v)
      nodes.waitForHeightAriseAndTxPresent(transferTransaction.id)
      if (v > 2) {
        transferTransaction.chainId shouldBe Some(AddressScheme.current.chainId)
        miner.transactionInfo[TransferTransactionInfo](transferTransaction.id).chainId shouldBe Some(AddressScheme.current.chainId)
      }

      miner.assertBalances(firstAddress, firstBalance - minFee - issueFee, firstEffBalance - minFee - issueFee)
      miner.assertBalances(secondAddress, secondBalance, secondEffBalance)
      miner.assertAssetBalance(firstAddress, issuedAssetId, 0)
      miner.assertAssetBalance(secondAddress, issuedAssetId, someAssetAmount)
    }
  }

  test("tac transfer changes tac balances and eff.b.") {
    for (v <- transferTxSupportedVersions) {
      val (firstBalance, firstEffBalance)   = miner.accountBalances(firstAddress)
      val (secondBalance, secondEffBalance) = miner.accountBalances(secondAddress)

      val transferId = sender.transfer(firstKeyPair, secondAddress, transferAmount, minFee, version = v).id

      nodes.waitForHeightAriseAndTxPresent(transferId)

      miner.assertBalances(firstAddress, firstBalance - transferAmount - minFee, firstEffBalance - transferAmount - minFee)
      miner.assertBalances(secondAddress, secondBalance + transferAmount, secondEffBalance + transferAmount)
    }
  }

  test("invalid signed tac transfer should not be in UTX or blockchain") {
    def invalidTx(timestamp: Long = System.currentTimeMillis, fee: Long = 100000): TransferTransaction =
      TransferTransaction
        .selfSigned(1.toByte, sender.keyPair, AddressOrAlias.fromString(sender.address).explicitGet(), Tac, 1, Tac, fee, ByteStr.empty, timestamp)
        .explicitGet()

    val (balance1, eff1) = miner.accountBalances(firstAddress)

    val invalidTxs = Seq(
      (invalidTx(timestamp = System.currentTimeMillis + 1.day.toMillis), "Transaction timestamp .* is more than .*ms in the future"),
      (invalidTx(fee = 99999), "Fee .* does not exceed minimal value")
    )

    for ((tx, diag) <- invalidTxs) {
      assertBadRequestAndResponse(sender.broadcastRequest(tx.json()), diag)
      nodes.foreach(_.ensureTxDoesntExist(tx.id().toString))
    }

    nodes.waitForHeightArise()
    miner.assertBalances(firstAddress, balance1, eff1)

  }

  test("can not make transfer without having enough effective balance") {
    for (v <- transferTxSupportedVersions) {
      val (secondBalance, secondEffBalance) = miner.accountBalances(secondAddress)

      assertApiErrorRaised(sender.transfer(secondKeyPair, firstAddress, secondEffBalance, minFee, version = v))
      nodes.waitForHeightArise()

      miner.assertBalances(secondAddress, secondBalance, secondEffBalance)
    }
  }

  test("can not make transfer without having enough balance") {
    for (v <- transferTxSupportedVersions) {
      val (secondBalance, secondEffBalance) = miner.accountBalances(secondAddress)

      assertBadRequestAndResponse(
        sender.transfer(secondKeyPair, firstAddress, secondBalance + 1.tac, minFee, version = v),
        "Attempt to transfer unavailable funds"
      )
      miner.assertBalances(secondAddress, secondBalance, secondEffBalance)
    }
  }

  test("can forge block with sending majority of some asset to self and to other account") {
    for (v <- transferTxSupportedVersions) {
      val (firstBalance, firstEffBalance)   = miner.accountBalances(firstAddress)
      val (secondBalance, secondEffBalance) = miner.accountBalances(secondAddress)

      val assetId = sender.issue(firstKeyPair, "second asset", "description", someAssetAmount, 0, reissuable = false, fee = issueFee).id

      nodes.waitForHeightAriseAndTxPresent(assetId)

      miner.assertBalances(firstAddress, firstBalance - issueFee, firstEffBalance - issueFee)
      miner.assertAssetBalance(firstAddress, assetId, someAssetAmount)

      val tx1 = sender.transfer(firstKeyPair, firstAddress, someAssetAmount, minFee, Some(assetId), version = v).id
      nodes.waitForHeightAriseAndTxPresent(tx1)

      val tx2 = sender.transfer(firstKeyPair, secondAddress, someAssetAmount / 2, minFee, Some(assetId), version = v).id
      nodes.waitForHeightAriseAndTxPresent(tx2)

      miner.assertBalances(firstAddress, firstBalance - issueFee - 2 * minFee, firstEffBalance - issueFee - 2 * minFee)
      miner.assertBalances(secondAddress, secondBalance, secondEffBalance)
    }
  }
}
