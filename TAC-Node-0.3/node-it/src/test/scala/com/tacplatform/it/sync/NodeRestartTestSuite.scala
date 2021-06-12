package com.tacplatform.it.sync

import com.typesafe.config.{Config, ConfigFactory}
import com.tacplatform.account.AddressOrAlias
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.api.TransactionInfo
import com.tacplatform.it.transactions.NodesFromDocker
import com.tacplatform.it.util._
import com.tacplatform.it.{ReportingTestName, WaitForHeight2}
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.transfer.TransferTransaction
import org.scalatest.{CancelAfterFailure, FreeSpec, Matchers}

class NodeRestartTestSuite extends FreeSpec with Matchers with WaitForHeight2 with CancelAfterFailure with ReportingTestName with NodesFromDocker {
  import NodeRestartTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs

  private def nodeA = nodes.head
  private def nodeB = nodes(1)

  "node should grow up to 5 blocks together and sync" in {
    nodes.waitForSameBlockHeadersAt(height = 5)
  }

  "create many addresses and check them after node restart" in {
    1 to 10 map (_ => nodeA.createKeyPair())
    val setOfAddresses      = nodeA.getAddresses
    val nodeAWithOtherPorts = docker.restartContainer(dockerNodes().head)
    val maxHeight           = nodes.map(_.height).max
    nodeAWithOtherPorts.getAddresses should contain theSameElementsAs setOfAddresses
    nodes.waitForSameBlockHeadersAt(maxHeight + 2)
  }

  "after restarting all the nodes, the duplicate transaction cannot be put into the blockchain" in {
    val txJson = TransferTransaction
      .selfSigned(
        1.toByte,
        nodeB.keyPair,
        AddressOrAlias.fromString(nodeA.address).explicitGet(),
        Tac,
        1.tac,
        Tac,
        minFee,
        ByteStr.empty,
        System.currentTimeMillis()
      )
      .explicitGet()
      .json()

    val tx = nodeB.signedBroadcast(txJson, waitForTx = true)
    nodeA.waitForTransaction(tx.id)

    val txHeight = nodeA.transactionInfo[TransactionInfo](tx.id).height

    nodes.waitForHeightArise()

    docker.restartContainer(dockerNodes().head)
    docker.restartContainer(dockerNodes()(1))

    nodes.waitForHeight(txHeight + 2)

    assertBadRequestAndMessage(
      nodeB.signedBroadcast(txJson, waitForTx = true),
      s"State check failed. Reason: Transaction ${tx.id} is already in the state on a height of $txHeight"
    )
  }

}

object NodeRestartTestSuite {
  import com.tacplatform.it.NodeConfigs._
  private val FirstNode = ConfigFactory.parseString(s"""
                                                         |tac {
                                                         |  synchronization.synchronization-timeout = 10s
                                                         |  blockchain.custom.functionality {
                                                         |    pre-activated-features.1 = 0
                                                         |  }
                                                         |  miner.quorum = 0
                                                         |  wallet {
                                                         |     file = "/tmp/wallet.dat"
                                                         |     password = "bla"
                                                         |  }
                                                         |
                                                         |}""".stripMargin)

  private val SecondNode = ConfigFactory.parseString(s"""
                                                            |tac {
                                                            |  synchronization.synchronization-timeout = 10s
                                                            |  blockchain.custom.functionality {
                                                            |    pre-activated-features.1 = 0
                                                            |  }
                                                            |  miner.enable = no
                                                            |  wallet {
                                                            |     file = "/tmp/wallet.dat"
                                                            |     password = "bla"
                                                            |  }
                                                            |}""".stripMargin)

  val Configs: Seq[Config] = Seq(
    FirstNode.withFallback(Default.head),
    SecondNode.withFallback(Default(1))
  )

}
