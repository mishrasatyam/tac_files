package com.tacplatform.it.sync.smartcontract

import com.typesafe.config.{Config, ConfigFactory}
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.NodesFromDocker
import com.tacplatform.it.util._
import com.tacplatform.it.{ReportingTestName, WaitForHeight2}
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.transaction.smart.script.ScriptCompiler
import org.scalatest.{CancelAfterFailure, FreeSpec, Matchers}

class UTXAllowance extends FreeSpec with Matchers with WaitForHeight2 with CancelAfterFailure with ReportingTestName with NodesFromDocker {
  import UTXAllowance._

  override protected def nodeConfigs: Seq[Config] = Configs

  private def nodeA = nodes.head
  private def nodeB = nodes.last

  "create two nodes with scripted accounts and check UTX" in {
    val accounts = List(nodeA, nodeB).map(i => {

      val acc = i.createKeyPair()

      i.transfer(i.keyPair, acc.toAddress.toString, 10.tac, 0.005.tac, None, waitForTx = true)

      val scriptText = s"""true""".stripMargin
      val script     = ScriptCompiler(scriptText, isAssetScript = false, ScriptEstimatorV2).explicitGet()._1.bytes().base64
      i.setScript(acc, Some(script), setScriptFee, waitForTx = true)

      acc
    })

    assertBadRequestAndMessage(
      nodeA
        .transfer(
          accounts.head,
          recipient = accounts.head.toAddress.toString,
          assetId = None,
          amount = 1.tac,
          fee = minFee + 0.004.tac,
          version = 2
        ),
      "transactions from scripted accounts are denied from UTX pool"
    )

    val txBId =
      nodeB
        .transfer(
          accounts(1),
          recipient = accounts(1).toAddress.toString,
          assetId = None,
          amount = 1.01.tac,
          fee = minFee + 0.004.tac,
          version = 2
        )
        .id

    nodes.waitForHeightArise()
    nodeA.findTransactionInfo(txBId) shouldBe None
  }

}

object UTXAllowance {
  import com.tacplatform.it.NodeConfigs._
  private val FirstNode = ConfigFactory.parseString(s"""
                                                         |tac {
                                                         |  utx.allow-transactions-from-smart-accounts = false
                                                         |  miner {
                                                         |      quorum = 0
                                                         |      enable = yes
                                                         |  }
                                                         |}""".stripMargin)

  private val SecondNode = ConfigFactory.parseString(s"""
                                                          |tac {
                                                          |  utx.allow-transactions-from-smart-accounts = true
                                                          |  miner {
                                                          |      enable = no
                                                          |  }
                                                          |}""".stripMargin)

  val Configs: Seq[Config] = Seq(
    FirstNode.withFallback(Default.head),
    SecondNode.withFallback(Default(1))
  )

}
