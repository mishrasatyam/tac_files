package com.tacplatform.it.sync.smartcontract

import com.typesafe.config.Config
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.it.NodeConfigs
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.lang.directives.DirectiveDictionary
import com.tacplatform.lang.directives.values.StdLibVersion.V5
import com.tacplatform.lang.directives.values.{StdLibVersion, V3}
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction.smart.InvokeScriptTransaction.Payment
import com.tacplatform.transaction.smart.script.ScriptCompiler

class InvokePaymentsAvailabilitySuite extends BaseTransactionSuite {

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .overrideBase(
        _.preactivatedFeatures(
          (BlockchainFeatures.Ride4DApps.id, 0),
          (BlockchainFeatures.BlockV5.id, 0),
          (BlockchainFeatures.SynchronousCalls.id, 0)
        )
      )
      .withDefault(1)
      .buildNonConflicting()

  private lazy val (caller, callerAddress)           = (firstKeyPair, firstAddress)
  private lazy val (callingDApp, callingDAppAddress) = (secondKeyPair, secondAddress)
  private lazy val (proxyDApp, proxyDAppAddress)     = (thirdKeyPair, thirdAddress)

  private def syncDApp(dApp: String) =
    ScriptCompiler(
      s"""
       |{-# STDLIB_VERSION 5 #-}
       |{-# CONTENT_TYPE DAPP #-}
       |{-# SCRIPT_TYPE ACCOUNT #-}
       |
       | let dApp2 = Address(base58'$dApp')
       |
       | @Callable(inv)
       | func default() = {
       |    let pmt = inv.payments[0]
       |    strict invokeV4 = dApp2.invoke("default", nil, [AttachedPayment(pmt.assetId, pmt.amount)])
       |    [
       |       IntegerEntry("balance_self", this.assetBalance(pmt.assetId.value())),
       |       IntegerEntry("balance_calling_dApp", dApp2.assetBalance(pmt.assetId.value()))
       |    ]
       | }
       |
         """.stripMargin,
      isAssetScript = false,
      ScriptEstimatorV3
    ).explicitGet()._1.bytes().base64

  private def dApp(version: StdLibVersion) = {
    val data =
      if (version > V3)
        s"""
           |let pmtAssetId = inv.payments[0].assetId.value()
           |[
           |  IntegerEntry("balance_self", this.assetBalance(pmtAssetId)),
           |  IntegerEntry("balance_caller", inv.caller.assetBalance(pmtAssetId))
           |]
         """.stripMargin
      else
        s"""
           |let pmtAssetId = inv.payment.value().assetId.value()
           |WriteSet([
           |  DataEntry("balance_self", this.assetBalance(pmtAssetId)),
           |  DataEntry("balance_caller", inv.caller.assetBalance(pmtAssetId))
           |])
         """.stripMargin

    ScriptCompiler(
      s"""
       | {-# STDLIB_VERSION ${version.id}  #-}
       | {-# CONTENT_TYPE   DAPP           #-}
       | {-# SCRIPT_TYPE    ACCOUNT        #-}
       |
       | @Callable(inv)
       | func default() = {
       |   $data
       | }
     """.stripMargin,
      isAssetScript = false,
      ScriptEstimatorV3
    ).explicitGet()._1.bytes().base64
  }

  private val paymentAmount = 12345
  private val issueAmount   = 1000 * 1000

  test("payments availability in sync call") {
    val assetId = sender.issue(caller, quantity = issueAmount, waitForTx = true).id
    val asset   = IssuedAsset(ByteStr.decodeBase58(assetId).get)
    sender.setScript(proxyDApp, Some(syncDApp(callingDAppAddress)), waitForTx = true)

    DirectiveDictionary[StdLibVersion].all
      .filter(_ >= V3)
      .foreach { callingDAppVersion =>
        sender.setScript(callingDApp, Some(dApp(callingDAppVersion)), waitForTx = true)

        val callerStartBalance      = sender.assetBalance(callerAddress, assetId).balance
        val proxyStartBalance       = sender.assetBalance(proxyDAppAddress, assetId).balance
        val callingDAppStartBalance = sender.assetBalance(callingDAppAddress, assetId).balance

        sender.invokeScript(caller, proxyDAppAddress, payment = Seq(Payment(paymentAmount, asset)), fee = invokeFee, waitForTx = true)
        sender.assetBalance(callerAddress, assetId).balance shouldBe callerStartBalance - paymentAmount

        val expectingProxyDAppBalance = 0
        List(
          sender.assetBalance(proxyDAppAddress, assetId).balance,
          sender.getData(proxyDAppAddress, "balance_self").head.value
        ).foreach(_ shouldBe proxyStartBalance + expectingProxyDAppBalance)

        val expectingCallingDAppBalance = paymentAmount
        List(
          sender.assetBalance(callingDAppAddress, assetId).balance,
          sender.getData(proxyDAppAddress, "balance_calling_dApp").head.value
        ).foreach(_ shouldBe callingDAppStartBalance + expectingCallingDAppBalance)

        val expectingCallingDAppBalanceInsideCallingDApp = if (callingDAppVersion >= V5) paymentAmount else 0
        val expectingProxyDAppBalanceInsideCallingDApp   = paymentAmount - expectingCallingDAppBalanceInsideCallingDApp
        sender.getData(callingDAppAddress, "balance_self").head.value shouldBe callingDAppStartBalance + expectingCallingDAppBalanceInsideCallingDApp
        sender.getData(callingDAppAddress, "balance_caller").head.value shouldBe proxyStartBalance + expectingProxyDAppBalanceInsideCallingDApp
      }
  }
}
