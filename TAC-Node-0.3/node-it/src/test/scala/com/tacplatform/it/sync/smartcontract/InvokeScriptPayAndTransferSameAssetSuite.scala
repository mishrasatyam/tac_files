package com.tacplatform.it.sync.smartcontract

import com.tacplatform.api.http.ApiError.TransactionNotAllowedByAssetScript
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync.{setScriptFee, smartFee, smartMinFee}
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.smart.InvokeScriptTransaction.Payment
import com.tacplatform.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class InvokeScriptPayAndTransferSameAssetSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val estimator = ScriptEstimatorV2

  private def dApp     = firstKeyPair
  private def caller   = secondKeyPair
  private def receiver = thirdKeyPair

  private lazy val dAppAddress: String = dApp.toAddress.toString
  private lazy val callerAddress: String = caller.toAddress.toString
  private lazy val receiverAddress: String = receiver.toAddress.toString

  var dAppInitBalance: Long     = 0
  var callerInitBalance: Long   = 0
  var receiverInitBalance: Long = 0
  val assetQuantity: Long       = 15
  var assetId: String           = ""
  var smartAssetId: String      = ""
  var rejAssetId: String        = ""

  test("_issue and transfer asset") {
    assetId = sender.issue(caller, "Asset", "a", assetQuantity, 0).id

    val script = Some(ScriptCompiler.compile("true", estimator).explicitGet()._1.bytes().base64)
    smartAssetId = sender.issue(caller, "Smart", "s", assetQuantity, 0, script = script).id

    val scriptText  = "match tx {case _:TransferTransaction => false case _ => true}"
    val smartScript = Some(ScriptCompiler.compile(scriptText, estimator).explicitGet()._1.bytes().base64)
    rejAssetId = sender.issue(caller, "Reject", "r", assetQuantity, 0, script = smartScript, waitForTx = true).id
  }


  test("_set script to dApp account and transfer out all tac") {
    val dAppBalance = sender.accountBalances(dAppAddress)._1
    sender.transfer(dApp, callerAddress, dAppBalance - smartMinFee - setScriptFee, smartMinFee, waitForTx = true).id

    val dAppScript        = ScriptCompiler.compile(s"""
          |{-# STDLIB_VERSION 3 #-}
          |{-# CONTENT_TYPE DAPP #-}
          |
          |let receiver = Address(base58'$receiverAddress')
          |
          |@Callable(i)
          |func resendPayment() = {
          |  if (isDefined(i.payment)) then
          |    let pay = extract(i.payment)
          |    TransferSet([ScriptTransfer(receiver, 1, pay.assetId)])
          |  else throw("need payment in TAC or any Asset")
          |}
        """.stripMargin, estimator).explicitGet()._1
    sender.setScript(dApp, Some(dAppScript.bytes().base64), waitForTx = true).id

  }

  test("dApp can transfer payed asset if its own balance is 0") {
    dAppInitBalance = sender.accountBalances(dAppAddress)._1
    callerInitBalance = sender.accountBalances(callerAddress)._1
    receiverInitBalance = sender.accountBalances(receiverAddress)._1

    val paymentAmount = 10

    invoke("resendPayment", paymentAmount, issued(assetId))

    sender.accountBalances(dAppAddress)._1 shouldBe dAppInitBalance
    sender.accountBalances(callerAddress)._1 shouldBe callerInitBalance - smartMinFee
    sender.accountBalances(receiverAddress)._1 shouldBe receiverInitBalance

    sender.assetBalance(dAppAddress, assetId).balance shouldBe paymentAmount - 1
    sender.assetBalance(callerAddress, assetId).balance shouldBe assetQuantity - paymentAmount
    sender.assetBalance(receiverAddress, assetId).balance shouldBe 1
  }

  test("dApp can transfer payed smart asset if its own balance is 0") {
    dAppInitBalance = sender.accountBalances(dAppAddress)._1
    callerInitBalance = sender.accountBalances(callerAddress)._1
    receiverInitBalance = sender.accountBalances(receiverAddress)._1

    val paymentAmount = 10
    val fee           = smartMinFee + smartFee * 2

    invoke("resendPayment", paymentAmount, issued(smartAssetId), fee)

    sender.accountBalances(dAppAddress)._1 shouldBe dAppInitBalance
    sender.accountBalances(callerAddress)._1 shouldBe callerInitBalance - fee
    sender.accountBalances(receiverAddress)._1 shouldBe receiverInitBalance

    sender.assetBalance(dAppAddress, smartAssetId).balance shouldBe paymentAmount - 1
    sender.assetBalance(callerAddress, smartAssetId).balance shouldBe assetQuantity - paymentAmount
    sender.assetBalance(receiverAddress, smartAssetId).balance shouldBe 1
  }

  test("dApp can't transfer payed smart asset if it rejects transfers and its own balance is 0") {
    dAppInitBalance = sender.accountBalances(dAppAddress)._1
    callerInitBalance = sender.accountBalances(callerAddress)._1
    receiverInitBalance = sender.accountBalances(receiverAddress)._1

    val paymentAmount = 10
    val fee           = smartMinFee + smartFee * 2

    assertApiError(invoke("resendPayment", paymentAmount, issued(rejAssetId), fee),
      AssertiveApiError(TransactionNotAllowedByAssetScript.Id, "Transaction is not allowed by token-script")
    )
  }

  test("dApp can transfer payed Tac if its own balance is 0") {
    dAppInitBalance = sender.accountBalances(dAppAddress)._1
    callerInitBalance = sender.accountBalances(callerAddress)._1
    receiverInitBalance = sender.accountBalances(receiverAddress)._1

    dAppInitBalance shouldBe 0

    val paymentAmount    = 10
    invoke("resendPayment", paymentAmount)

    sender.accountBalances(dAppAddress)._1 shouldBe dAppInitBalance + paymentAmount - 1
    sender.accountBalances(callerAddress)._1 shouldBe callerInitBalance - paymentAmount - smartMinFee
    sender.accountBalances(receiverAddress)._1 shouldBe receiverInitBalance + 1
  }

  def issued(assetId: String): Asset = IssuedAsset(ByteStr.decodeBase58(assetId).get)

  def invoke(func: String, amount: Long, asset: Asset = Tac, fee: Long = 500000): String = {
    sender
      .invokeScript(
        caller,
        dAppAddress,
        Some(func),
        payment = Seq(Payment(amount, asset)),
        fee = fee,
        waitForTx = true
      )
      ._1.id
  }

}
