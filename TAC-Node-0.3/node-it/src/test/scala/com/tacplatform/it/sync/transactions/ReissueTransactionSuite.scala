package com.tacplatform.it.sync.transactions

import com.typesafe.config.Config
import com.tacplatform.account.AddressScheme
import com.tacplatform.api.http.ApiError.StateCheckFailed
import com.tacplatform.it.NodeConfigs
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.api.TransactionInfo
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.it.util._
import com.tacplatform.transaction.assets.ReissueTransaction

class ReissueTransactionSuite extends BaseTransactionSuite {

  test("asset reissue changes issuer's asset balance; issuer's tac balance is decreased by fee") {
    for (v <- reissueTxSupportedVersions) {
      val (balance, effectiveBalance) = miner.accountBalances(firstAddress)

      val issuedAssetId = sender.issue(firstKeyPair, "name2", "description2", someAssetAmount, decimals = 2, reissuable = true, issueFee).id
      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)

      val reissueTx = sender.reissue(firstKeyPair, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueReducedFee, version = v)
      nodes.waitForHeightAriseAndTxPresent(reissueTx.id)
      if (v > 2) {
        reissueTx.chainId shouldBe Some(AddressScheme.current.chainId)
        sender.transactionInfo[TransactionInfo](reissueTx.id).chainId shouldBe Some(AddressScheme.current.chainId)
      }
      miner.assertBalances(firstAddress, balance - issueFee - reissueReducedFee, effectiveBalance - issueFee - reissueReducedFee)
      miner.assertAssetBalance(firstAddress, issuedAssetId, 2 * someAssetAmount)
    }

    miner.transactionsByAddress(firstAddress, limit = 100)
      .count(_._type == ReissueTransaction.typeId) shouldBe reissueTxSupportedVersions.length
  }

  test("can't reissue not reissuable asset") {
    for (v <- reissueTxSupportedVersions) {
      val (balance, effectiveBalance) = miner.accountBalances(firstAddress)

      val issuedAssetId = sender.issue(firstKeyPair, "name2", "description2", someAssetAmount, decimals = 2, reissuable = false, issueFee).id
      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)

      assertBadRequestAndMessage(
        sender.reissue(firstKeyPair, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueReducedFee, version = v),
        "Asset is not reissuable"
      )
      nodes.waitForHeightArise()

      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
    }
  }

  test("not able to reissue if cannot pay fee - less than required") {
    for (v <- reissueTxSupportedVersions) {
      val issuedAssetId = sender.issue(firstKeyPair, "name3", "description3", someAssetAmount, decimals = 2, reissuable = true, issueFee).id

      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

      assertApiError(sender.reissue(firstKeyPair, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueReducedFee - 1, version = v)) { error =>
        error.id shouldBe StateCheckFailed.Id
        error.message should include(s"Fee for ReissueTransaction (${reissueReducedFee - 1} in TAC) does not exceed minimal value of $reissueReducedFee TAC.")
      }
    }
  }

  test("not able to reissue if cannot pay fee - insufficient funds") {
    for (v <- reissueTxSupportedVersions) {
      val (balance, effectiveBalance) = miner.accountBalances(firstAddress)
      val reissueFee = effectiveBalance + 1.tac

      val issuedAssetId = sender.issue(firstKeyPair, "name4", "description4", someAssetAmount, decimals = 2, reissuable = true, issueFee).id

      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

      assertBadRequestAndMessage(
        sender.reissue(firstKeyPair, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueFee, version = v),
        "Accounts balance errors"
      )
      nodes.waitForHeightArise()

      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
    }
  }

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .withDefault(1)
      .withSpecial(_.nonMiner)
      .buildNonConflicting()
}
