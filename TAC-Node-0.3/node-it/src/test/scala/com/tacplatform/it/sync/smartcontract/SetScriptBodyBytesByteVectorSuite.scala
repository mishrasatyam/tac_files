package com.tacplatform.it.sync.smartcontract

import com.typesafe.config.Config
import com.tacplatform.account.KeyPair
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.NodeConfigs
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.api.TransactionInfo
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.transaction.smart.script.ScriptCompiler

class SetScriptBodyBytesByteVectorSuite extends BaseTransactionSuite {
  private def compile(scriptText: String) =
    ScriptCompiler.compile(scriptText, ScriptEstimatorV2).explicitGet()._1.bytes().base64

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .withDefault(1)
      .buildNonConflicting()

  private val expectedBodyBytesSize = 32815

  private val verifierV3 =
    compile(
      s"""
         |{-# STDLIB_VERSION 3 #-}
         |{-# CONTENT_TYPE EXPRESSION #-}
         |
         | match tx {
         |    case sstx: SetScriptTransaction =>
         |      sstx.bodyBytes.size() == $expectedBodyBytesSize
         |
         |   case _ =>
         |      throw("unexpected")
         | }
         |
       """.stripMargin
    )

  private val verifierV4 =
    compile(
      s"""
         |{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE EXPRESSION #-}
         |
         | match tx {
         |   case sstx: SetScriptTransaction =>
         |     sstx.bodyBytes.size() == $expectedBodyBytesSize                 &&
         |     sigVerify(sstx.bodyBytes, sstx.proofs[0], sstx.senderPublicKey)
         |
         |  case _ =>
         |     throw("unexpected")
         | }
         |
       """.stripMargin
    )

  private def dApp(letCount: Int) = {
    val body = (1 to letCount).map(i => s"let a$i = 1 ").mkString
    compile(
      s"""
         | {-# STDLIB_VERSION 4 #-}
         | {-# CONTENT_TYPE DAPP #-}
         | {-# SCRIPT_TYPE ACCOUNT #-}
         |
         | $body
       """.stripMargin
    )
  }

  test("big SetScript body bytes") {
    checkByteVectorLimit(firstKeyPair, verifierV3)
    checkByteVectorLimit(secondKeyPair, verifierV4)

    (the[RuntimeException] thrownBy dApp(1782)).getMessage shouldBe "Script is too large: 32780 bytes > 32768 bytes"
  }

  private def checkByteVectorLimit(address: KeyPair, verifier: String) = {
    val setScriptId = sender.setScript(address, Some(verifier), setScriptFee, waitForTx = true).id
    sender.transactionInfo[TransactionInfo](setScriptId).script.get.startsWith("base64:") shouldBe true

    val scriptInfo = sender.addressScriptInfo(address.toAddress.toString)
    scriptInfo.script.isEmpty shouldBe false
    scriptInfo.scriptText.isEmpty shouldBe false
    scriptInfo.script.get.startsWith("base64:") shouldBe true

    sender.setScript(address, Some(dApp(1781)), setScriptFee + smartFee, waitForTx = true)
  }
}
