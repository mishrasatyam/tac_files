package com.tacplatform.it.sync.smartcontract
import com.tacplatform.api.http.ApiError.ScriptExecutionError
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync.{setScriptFee, _}
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.lang.v1.compiler.Terms.CONST_BOOLEAN
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.state.BinaryDataEntry
import com.tacplatform.transaction.TxVersion
import com.tacplatform.transaction.smart.script.ScriptCompiler

// because of SC-655 bug
class InvokeSmartAssetFailSuite extends BaseTransactionSuite {
  private lazy val caller = firstKeyPair
  private lazy val dApp   = secondKeyPair

  val dAppText =
    """
      |{-# STDLIB_VERSION 4 #-}
      |{-# CONTENT_TYPE DAPP #-}
      |{-# SCRIPT_TYPE ACCOUNT #-}
      |
      |let bob = addressFromPublicKey(base58'BzFTfc4TB9s25d8b3sfhptj4STZafEM2FNkR5kQ8mJeA')
      |
      |@Callable(i)
      |func some(fail: Boolean) = {
      |  let issue = Issue("asset", "test asset", 500, 2, true)
      |  let assetId = if fail then this.getBinaryValue("assetId") else issue.calculateAssetId()
      |
      |  let result = [
      |    BinaryEntry("bin", i.transactionId),
      |    BooleanEntry("bool", true),
      |    IntegerEntry("int", i.fee),
      |    StringEntry("str", i.caller.toString()),
      |    DeleteEntry("remove")
      |  ]
      |
      |  if fail then {
      |    result ++ [
      |      Reissue(assetId, 10, false)
      |    ]
      |  } else {
      |    result ++ [
      |      issue,
      |      Reissue(assetId, 10, false),
      |      Burn(assetId, 5),
      |      SponsorFee(assetId, 2),
      |      ScriptTransfer(bob, 7, assetId)
      |    ]
      |  }
      |
      |}
    """.stripMargin

  test("extracted funcs") {
    val assetId = ByteStr.decodeBase58(sender.issue(caller, waitForTx = true).id).get
    val data    = List(BinaryDataEntry("assetId", assetId))
    val dataFee = calcDataFee(data, TxVersion.V2)
    sender.putData(dApp, data, fee = dataFee, waitForTx = true)

    val script = ScriptCompiler.compile(dAppText, ScriptEstimatorV3).explicitGet()._1.bytes().base64
    sender.setScript(dApp, Some(script), setScriptFee, waitForTx = true)
    assertApiError(
      sender.invokeScript(caller, dApp.toAddress.toString, Some("some"), List(CONST_BOOLEAN(true))),
      AssertiveApiError(ScriptExecutionError.Id, "Error while executing account-script: Asset was issued by other address")
    )
  }
}
