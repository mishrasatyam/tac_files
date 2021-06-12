package com.tacplatform.it.sync.smartcontract

import com.tacplatform.api.http.ApiError.ScriptExecutionError
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.{Base58, EitherExt2}
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync.{issueFee, setScriptFee, smartFee}
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction.smart.InvokeScriptTransaction.Payment
import com.tacplatform.transaction.smart.script.ScriptCompiler

class PaymentValidationSuite extends BaseTransactionSuite {

  test("payment's validation order check") {
    val dApp = firstKeyPair
    val caller = secondKeyPair
    val (wrKey, wrValue) = ("key", "value")

    val sourceV4 =
      s"""{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE DAPP #-}
         |{-# SCRIPT_TYPE ACCOUNT #-}
         |
        |@Callable(i)
         |func write() = {
         |  [StringEntry("$wrKey", "$wrValue")]
         |}
      """.stripMargin
    val scriptV4 = ScriptCompiler.compile(sourceV4, ScriptEstimatorV3).explicitGet()._1.bytes().base64
    sender.setScript(dApp, Some(scriptV4), setScriptFee, waitForTx = true)

    val scr = ScriptCompiler(
      s"""
         |{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE EXPRESSION #-}
         |{-# SCRIPT_TYPE ASSET #-}
         |
         |getStringValue(addressFromString("${dApp.toAddress.toString}").value(), "$wrKey") == "$wrValue"
         """.stripMargin,
      isAssetScript = true,
      ScriptEstimatorV3
    ).explicitGet()._1.bytes().base64
    val smartAssetId = sender.issue(caller, script = Some(scr), fee = issueFee + smartFee, waitForTx = true).id

    assertApiError(
      sender.invokeScript(caller, dApp.toAddress.toString, func = Some("write"),
        payment = Seq(Payment(1000L, IssuedAsset(ByteStr(Base58.decode(smartAssetId))))), fee = issueFee)) {
      err =>
        err.message should include regex "called on unit"
        err.id shouldBe ScriptExecutionError.Id
    }

  }
}