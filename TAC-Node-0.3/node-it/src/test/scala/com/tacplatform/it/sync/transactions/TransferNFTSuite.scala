package com.tacplatform.it.sync.transactions

import com.tacplatform.account.KeyPair
import com.tacplatform.api.http.ApiError.ScriptExecutionError
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.NTPTime
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.api.Transaction
import com.tacplatform.it.sync.{calcMassTransferFee, setScriptFee, _}
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.it.util._
import com.tacplatform.lang.v1.compiler.Terms
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.assets.exchange.{AssetPair, ExchangeTransaction, Order}
import com.tacplatform.transaction.smart.InvokeScriptTransaction
import com.tacplatform.transaction.smart.script.ScriptCompiler
import com.tacplatform.transaction.transfer.MassTransferTransaction.Transfer

class TransferNFTSuite extends BaseTransactionSuite with NTPTime {
  val assetName        = "NFTAsset"
  val assetDescription = "my asset description"

  private def caller   = firstKeyPair
  private def dApp     = secondKeyPair
  private def receiver = thirdKeyPair

  private lazy val callerAddress: String = caller.toAddress.toString
  private lazy val dAppAddress: String   = dApp.toAddress.toString
  test("NFT should be correctly transferred via transfer transaction") {
    val nftAsset = sender.issue(caller, assetName, assetDescription, 1, 0, reissuable = false, 1.tac / 1000, waitForTx = true).id
    sender.transfer(caller, dAppAddress, 1, minFee, Some(nftAsset), waitForTx = true)

    sender.assetBalance(callerAddress, nftAsset).balance shouldBe 0
    sender.nftList(callerAddress, 10).map(info => info.assetId) shouldNot contain(nftAsset)
    sender.assetBalance(dAppAddress, nftAsset).balance shouldBe 1
    sender.nftList(dAppAddress, 10).map(info => info.assetId) should contain(nftAsset)
  }

  test("NFT should be correctly transferred via invoke script transaction") {
    val nftAsset = sender.issue(caller, assetName, assetDescription, 1, 0, reissuable = false, 1.tac / 1000, waitForTx = true).id
    val scriptText =
      s"""
         |{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE DAPP #-}
         |{-# SCRIPT_TYPE ACCOUNT #-}
         |
         |@Callable(i)
         |func nftTransferToDapp() = {
         |    let pmt = i.payments[0];
         |    [ ScriptTransfer(this, pmt.amount, pmt.assetId) ]
         |}
         |
         |@Callable(i)
         |func nftPaymentTransferToThirdAddress(address: String) = {
         |    let thirdAddress = Address(fromBase58String(address))
         |    let pmt = i.payments[0];
         |    [ ScriptTransfer(thirdAddress, pmt.amount, pmt.assetId) ]
         |}
         |
         |@Callable(i)
         |func transferAsPayment() = []
         |
         |@Callable(i)
         |func nftTransferToSelf() = {
         |    let pmt = i.payments[0];
         |    [ ScriptTransfer(i.caller, pmt.amount, pmt.assetId) ]
         |}
         |
         |@Callable(i)
         |func transferFromDappToAddress(address: String) = {
         |    let recipient = Address(fromBase58String(address))
         |    [ ScriptTransfer(recipient, 1, base58'$nftAsset') ]
         |}
         |
         |@Verifier(t)
         |func verify() = true
         |""".stripMargin
    val script = ScriptCompiler.compile(scriptText, ScriptEstimatorV2).explicitGet()._1.bytes().base64
    sender.setScript(dApp, Some(script), setScriptFee, waitForTx = true)
    def invokeTransfer(
        caller: KeyPair,
        functionName: String,
        args: List[Terms.EXPR] = List.empty,
        payment: Seq[InvokeScriptTransaction.Payment] = Seq.empty
    ): Transaction = {
      sender.invokeScript(caller, dAppAddress, Some(functionName), payment = payment, args = args, fee = 1300000, waitForTx = true)._1
    }
    val nftPayment = Seq(InvokeScriptTransaction.Payment(1, Asset.fromString(Some(nftAsset))))

    assertApiError(
      invokeTransfer(caller, "nftTransferToDapp", payment = nftPayment),
      AssertiveApiError(ScriptExecutionError.Id, "Error while executing account-script: DApp self-transfer is forbidden since V4")
    )

    sender.transfer(caller, dAppAddress, 1, assetId = Some(nftAsset), waitForTx = true)

    invokeTransfer(caller, "transferFromDappToAddress", args = List(Terms.CONST_STRING(receiver.toAddress.toString).explicitGet()))
    sender.assetBalance(dAppAddress, nftAsset).balance shouldBe 0
    sender.nftList(dAppAddress, 10).map(info => info.assetId) shouldNot contain(nftAsset)
    sender.assetBalance(receiver.toAddress.toString, nftAsset).balance shouldBe 1
    sender.nftList(receiver.toAddress.toString, 10).map(info => info.assetId) should contain(nftAsset)

    invokeTransfer(receiver, "nftTransferToSelf", payment = Seq(InvokeScriptTransaction.Payment(1, Asset.fromString(Some(nftAsset)))))
    sender.assetBalance(dAppAddress, nftAsset).balance shouldBe 0
    sender.nftList(dAppAddress, 10).map(info => info.assetId) shouldNot contain(nftAsset)
    sender.assetBalance(receiver.toAddress.toString, nftAsset).balance shouldBe 1
    sender.nftList(receiver.toAddress.toString, 10).map(info => info.assetId) should contain(nftAsset)

    invokeTransfer(
      receiver,
      "nftPaymentTransferToThirdAddress",
      args = List(Terms.CONST_STRING(callerAddress).explicitGet()),
      payment = Seq(InvokeScriptTransaction.Payment(1, Asset.fromString(Some(nftAsset))))
    )
    sender.assetBalance(receiver.toAddress.toString, nftAsset).balance shouldBe 0
    sender.nftList(receiver.toAddress.toString, 10).map(info => info.assetId) shouldNot contain(nftAsset)
    sender.assetBalance(dAppAddress, nftAsset).balance shouldBe 0
    sender.nftList(dAppAddress, 10).map(info => info.assetId) shouldNot contain(nftAsset)
    sender.assetBalance(callerAddress, nftAsset).balance shouldBe 1
    sender.nftList(callerAddress, 10).map(info => info.assetId) should contain(nftAsset)

    invokeTransfer(caller, "transferAsPayment", payment = Seq(InvokeScriptTransaction.Payment(1, Asset.fromString(Some(nftAsset)))))
    sender.assetBalance(callerAddress, nftAsset).balance shouldBe 0
    sender.nftList(callerAddress, 10).map(info => info.assetId) shouldNot contain(nftAsset)
    sender.assetBalance(dAppAddress, nftAsset).balance shouldBe 1
    sender.nftList(dAppAddress, 10).map(info => info.assetId) should contain(nftAsset)
  }

  test("NFT should be correctly transferred via mass transfer transaction") {
    val nftAsset = sender.issue(caller, assetName, assetDescription, 1, 0, reissuable = false, 1.tac / 1000, waitForTx = true).id
    sender.massTransfer(caller, List(Transfer(receiver.toAddress.toString, 1)), calcMassTransferFee(1), assetId = Some(nftAsset), waitForTx = true)

    sender.assetBalance(callerAddress, nftAsset).balance shouldBe 0
    sender.nftList(callerAddress, 10).map(info => info.assetId) shouldNot contain(nftAsset)
    sender.assetBalance(receiver.toAddress.toString, nftAsset).balance shouldBe 1
    sender.nftList(receiver.toAddress.toString, 10).map(info => info.assetId) should contain(nftAsset)
  }

  test("NFT should correctly be transferred via exchange transaction") {
    val buyer   = KeyPair("buyer".getBytes("UTF-8"))
    val seller  = KeyPair("seller".getBytes("UTF-8"))
    val matcher = KeyPair("matcher".getBytes("UTF-8"))
    val transfers = List(
      Transfer(buyer.toAddress.toString, 10.tac),
      Transfer(seller.toAddress.toString, 10.tac),
      Transfer(matcher.toAddress.toString, 10.tac)
    )
    sender.massTransfer(caller, transfers, calcMassTransferFee(transfers.size), waitForTx = true)

    val nftAsset =
      sender.broadcastIssue(seller, assetName, assetDescription, 1, 0, reissuable = false, 1.tac / 1000, waitForTx = true, script = None).id
    val pair = AssetPair.createAssetPair(nftAsset, "TAC")
    val ts   = ntpTime.correctedTime()
    val buy = Order.buy(
      Order.V2,
      sender = buyer,
      matcher = matcher.publicKey,
      pair = pair.get,
      amount = 1,
      price = 1.tac,
      timestamp = ts,
      expiration = ts + Order.MaxLiveTime,
      matcherFee = matcherFee
    )
    val sell = Order.sell(
      Order.V2,
      sender = seller,
      matcher = matcher.publicKey,
      pair = pair.get,
      amount = 1,
      price = 1.tac,
      timestamp = ts,
      expiration = ts + Order.MaxLiveTime,
      matcherFee = matcherFee
    )

    val tx = ExchangeTransaction
      .signed(
        2.toByte,
        matcher = matcher.privateKey,
        order1 = buy,
        order2 = sell,
        amount = 1,
        price = 1.tac,
        buyMatcherFee = matcherFee,
        sellMatcherFee = matcherFee,
        fee = matcherFee,
        timestamp = ts
      )
      .explicitGet()
      .json()

    sender.signedBroadcast(tx, waitForTx = true)
    sender.nftList(buyer.toAddress.toString, 10).map(info => info.assetId) should contain oneElementOf List(nftAsset)
    sender.nftList(seller.toAddress.toString, 10).map(info => info.assetId) shouldNot contain atLeastOneElementOf List(nftAsset)
    sender.assetBalance(buyer.toAddress.toString, nftAsset).balance shouldBe 1
    sender.assetBalance(seller.toAddress.toString, nftAsset).balance shouldBe 0

  }

}
