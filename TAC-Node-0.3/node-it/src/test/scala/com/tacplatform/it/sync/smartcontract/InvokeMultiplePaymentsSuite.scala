package com.tacplatform.it.sync.smartcontract

import com.tacplatform.api.http.ApiError._
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.it.util._
import com.tacplatform.lang.v1.compiler.Terms.CONST_STRING
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.state._
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.smart.InvokeScriptTransaction.Payment
import com.tacplatform.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class InvokeMultiplePaymentsSuite extends BaseTransactionSuite with CancelAfterFailure {
  private def dApp   = firstKeyPair
  private def caller = secondKeyPair

  private lazy val dAppAddress: String   = dApp.toAddress.toString
  private lazy val callerAddress: String = caller.toAddress.toString

  private var asset1: IssuedAsset = _
  private var asset2: IssuedAsset = _

  test("can transfer to alias") {
    val dAppBalance   = sender.balance(dAppAddress).balance
    val callerBalance = sender.balance(callerAddress).balance

    sender
      .invokeScript(
        caller,
        dAppAddress,
        Some("f"),
        payment = Seq(Payment(1.tac, Tac)),
        args = List(CONST_STRING("recipientalias").explicitGet()),
        waitForTx = true
      )

    sender.balance(dAppAddress).balance shouldBe dAppBalance
    sender.balance(callerAddress).balance shouldBe callerBalance - smartMinFee
  }

  test("script should sheck if alias not exists") {
    val alias = "unknown"

    assertBadRequestAndMessage(
      sender
        .invokeScript(
          caller,
          dAppAddress,
          Some("f"),
          payment = Seq(Payment(1.tac, Tac)),
          args = List(CONST_STRING(alias).explicitGet())
        ),
      s"Alias 'alias:I:$alias"
    )

    assertBadRequestAndMessage(
      sender
        .invokeScript(
          caller,
          dAppAddress,
          Some("f"),
          payment = Seq(Payment(1.tac, Tac)),
          args = List(CONST_STRING(s"alias:I:$alias").explicitGet()),
          waitForTx = true
        ),
      "Alias should contain only following characters"
    )
  }

  test("can invoke with no payments") {
    sender.invokeScript(caller, dAppAddress, payment = Seq.empty, waitForTx = true)
    sender.getData(dAppAddress).size shouldBe 0
  }

  test("can invoke with single payment of Tac") {
    sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(1.tac, Tac)), waitForTx = true)
    sender.getData(dAppAddress).size shouldBe 2
    sender.getDataByKey(dAppAddress, "amount_0").as[IntegerDataEntry].value shouldBe 1.tac
    sender.getDataByKey(dAppAddress, "asset_0").as[BinaryDataEntry].value shouldBe ByteStr.empty
  }

  test("can invoke with single payment of asset") {
    sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(10, asset1)), waitForTx = true)
    sender.getData(dAppAddress).size shouldBe 2
    sender.getDataByKey(dAppAddress, "amount_0").as[IntegerDataEntry].value shouldBe 10
    sender.getDataByKey(dAppAddress, "asset_0").as[BinaryDataEntry].value shouldBe asset1.id
  }

  test("can invoke with two payments of Tac") {
    sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(5, Tac), Payment(17, Tac)), waitForTx = true)
    sender.getData(dAppAddress).size shouldBe 4
    sender.getDataByKey(dAppAddress, "amount_0").as[IntegerDataEntry].value shouldBe 5
    sender.getDataByKey(dAppAddress, "asset_0").as[BinaryDataEntry].value shouldBe ByteStr.empty
    sender.getDataByKey(dAppAddress, "amount_1").as[IntegerDataEntry].value shouldBe 17
    sender.getDataByKey(dAppAddress, "asset_1").as[BinaryDataEntry].value shouldBe ByteStr.empty
  }

  test("can invoke with two payments of the same asset") {
    sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(8, asset1), Payment(21, asset1)), waitForTx = true)
    sender.getData(dAppAddress).size shouldBe 4
    sender.getDataByKey(dAppAddress, "amount_0").as[IntegerDataEntry].value shouldBe 8
    sender.getDataByKey(dAppAddress, "asset_0").as[BinaryDataEntry].value shouldBe asset1.id
    sender.getDataByKey(dAppAddress, "amount_1").as[IntegerDataEntry].value shouldBe 21
    sender.getDataByKey(dAppAddress, "asset_1").as[BinaryDataEntry].value shouldBe asset1.id
  }

  test("can invoke with two payments of different assets") {
    sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(3, asset1), Payment(6, asset2)), waitForTx = true)
    sender.getData(dAppAddress).size shouldBe 4
    sender.getDataByKey(dAppAddress, "amount_0").as[IntegerDataEntry].value shouldBe 3
    sender.getDataByKey(dAppAddress, "asset_0").as[BinaryDataEntry].value shouldBe asset1.id
    sender.getDataByKey(dAppAddress, "amount_1").as[IntegerDataEntry].value shouldBe 6
    sender.getDataByKey(dAppAddress, "asset_1").as[BinaryDataEntry].value shouldBe asset2.id
  }

  test("can't invoke with three payments") {
    assertApiError(
      sender.invokeScript(
        caller,
        dAppAddress,
        payment = Seq(Payment(3, Tac), Payment(6, Tac), Payment(7, Tac))
      )
    ) { error =>
      error.message should include("Script payment amount=3 should not exceed 2")
      error.id shouldBe StateCheckFailed.Id
      error.statusCode shouldBe 400
    }
  }

  test("can't attach more than balance") {
    val tacBalance  = sender.accountBalances(callerAddress)._1
    val asset1Balance = sender.assetBalance(callerAddress, asset1.id.toString).balance

    assertApiError(
      sender.invokeScript(
        caller,
        dAppAddress,
        payment = Seq(Payment(tacBalance - 1.tac, Tac), Payment(2.tac, Tac))
      )
    ) { error =>
      error.message should include("Transaction application leads to negative tac balance to (at least) temporary negative state")
      error.id shouldBe StateCheckFailed.Id
      error.statusCode shouldBe 400
    }

    assertApiError(
      sender.invokeScript(
        caller,
        dAppAddress,
        payment = Seq(Payment(asset1Balance - 1000, asset1), Payment(1001, asset1))
      )
    ) { error =>
      error.message should include("Transaction application leads to negative asset")
      error.id shouldBe StateCheckFailed.Id
      error.statusCode shouldBe 400
    }
  }

  test("can't attach leased Tac") {
    val tacBalance = sender.accountBalances(callerAddress)._1
    sender.lease(caller, dAppAddress, tacBalance - 1.tac, waitForTx = true)

    assertApiError(
      sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(0.75.tac, Tac), Payment(0.75.tac, Tac)))
    ) { error =>
      error.message should include("Accounts balance errors")
      error.id shouldBe StateCheckFailed.Id
      error.statusCode shouldBe 400
    }
  }

  test("can't attach with zero Tac amount") {
    assertApiError(
      sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(1, asset1), Payment(0, Tac))),
      NonPositiveAmount("0 of Tac")
    )
  }

  test("can't attach with zero asset amount") {
    assertApiError(
      sender.invokeScript(caller, dAppAddress, payment = Seq(Payment(0, asset1), Payment(1, Tac))),
      NonPositiveAmount(s"0 of $asset1")
    )
  }

  protected override def beforeAll(): Unit = {
    super.beforeAll()

    val source =
      s"""
         |{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE DAPP #-}
         |{-# SCRIPT_TYPE ACCOUNT #-}
         |
         |func parse(asset: ByteVector|Unit) = if asset.isDefined() then asset.value() else base58''
         |
         |@Callable(inv)
         |func default() = {
         |  let pmt = inv.payments
         |  nil
         |  ++ (if pmt.size() > 0 then [
         |    IntegerEntry("amount_0", pmt[0].amount),
         |    BinaryEntry("asset_0", pmt[0].assetId.parse())
         |  ] else nil)
         |  ++ (if pmt.size() > 1 then [
         |    IntegerEntry("amount_1", pmt[1].amount),
         |    BinaryEntry("asset_1", pmt[1].assetId.parse())
         |  ] else nil)
         |}
         |
         |@Callable(inv)
         |func f(toAlias: String) = {
         | if (${"sigVerify(base58'', base58'', base58'') ||" * 8} true)
         |  then {
         |    let pmt = inv.payments[0]
         |    #avoidbugcomment
         |    [ScriptTransfer(Alias(toAlias), pmt.amount, pmt.assetId)]
         |  }
         |  else {
         |    throw("unexpected")
         |  }
         |}
      """.stripMargin
    val script = ScriptCompiler.compile(source, ScriptEstimatorV2).explicitGet()._1.bytes().base64
    sender.setScript(dApp, Some(script), setScriptFee, waitForTx = true)

    asset1 = IssuedAsset(ByteStr.decodeBase58(sender.issue(caller, waitForTx = true).id).get)
    asset2 = IssuedAsset(ByteStr.decodeBase58(sender.issue(caller, waitForTx = true).id).get)
    sender.createAlias(caller, "recipientalias", smartMinFee, waitForTx = true)
  }
}
