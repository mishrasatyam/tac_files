package com.tacplatform.it.sync.smartcontract

import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.it.util._
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.smart.SetScriptTransaction
import com.tacplatform.transaction.smart.script.ScriptCompiler
import com.tacplatform.transaction.transfer.TransferTransaction
import org.scalatest.CancelAfterFailure

class BigLetChain extends BaseTransactionSuite with CancelAfterFailure {
  test("big let assignment chain") {
    val count = 280
    val scriptText =
      s"""
         | {-# STDLIB_VERSION 3    #-}
         | {-# CONTENT_TYPE   DAPP #-}
         |
         | @Verifier(tx)
         | func verify() = {
         |   let a0 = 1
         |   ${1 to count map (i => s"let a$i = a${i - 1}") mkString "\n"}
         |   a$count == a$count
         | }
       """.stripMargin

    val compiledScript = ScriptCompiler.compile(scriptText, ScriptEstimatorV2).explicitGet()._1

    val pkNewAddress = sender.createKeyPair()

    sender.transfer(firstKeyPair, pkNewAddress.toAddress.toString, 10.tac, minFee, waitForTx = true)

    val scriptSet          = SetScriptTransaction.selfSigned(1.toByte, pkNewAddress, Some(compiledScript), setScriptFee, System.currentTimeMillis())
    val scriptSetBroadcast = sender.signedBroadcast(scriptSet.explicitGet().json())
    nodes.waitForHeightAriseAndTxPresent(scriptSetBroadcast.id)

    val transfer = TransferTransaction.selfSigned(
      2.toByte,
      pkNewAddress,
      pkNewAddress.toAddress,
      Tac,
      1.tac,
      Tac,
      smartMinFee,
      ByteStr.empty,
      System.currentTimeMillis()
    )
    val transferBroadcast = sender.signedBroadcast(transfer.explicitGet().json())
    nodes.waitForHeightAriseAndTxPresent(transferBroadcast.id)
  }
}
