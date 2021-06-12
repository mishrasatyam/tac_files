package com.tacplatform.it.sync.transactions

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory.parseString
import com.tacplatform.account.Address
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.Node
import com.tacplatform.it.NodeConfigs._
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.{BaseTransactionSuite, NodesFromDocker}
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.transfer.TransferTransaction

class RebroadcastTransactionSuite extends BaseTransactionSuite with NodesFromDocker {

  import RebroadcastTransactionSuite._

  override protected def nodeConfigs: Seq[Config] =
    Seq(configWithRebroadcastAllowed.withFallback(NotMiner), configWithRebroadcastAllowed.withFallback(Miners.head))

  private def nodeA: Node = nodes.head
  private def nodeB: Node = nodes.last

  test("should rebroadcast a transaction if that's allowed in config") {
    val tx = TransferTransaction.selfSigned(2.toByte, nodeA.keyPair, Address.fromString(nodeB.address).explicitGet(), Tac, transferAmount, Tac, minFee, ByteStr.empty,  System.currentTimeMillis())
      .explicitGet()
      .json()

    val dockerNodeBId = docker.stopContainer(dockerNodes.apply().last)
    val txId          = nodeA.signedBroadcast(tx).id
    docker.startContainer(dockerNodeBId)
    nodeA.waitForPeers(1)

    nodeB.ensureTxDoesntExist(txId)
    nodeA.signedBroadcast(tx)
    nodeB.waitForUtxIncreased(0)
    nodeB.utxSize shouldBe 1
  }

  test("should not rebroadcast a transaction if that's not allowed in config") {
    dockerNodes().foreach(docker.restartNode(_, configWithRebroadcastNotAllowed))
    val tx = TransferTransaction
      .selfSigned(2.toByte, nodeA.keyPair, Address.fromString(nodeB.address).explicitGet(), Tac, transferAmount, Tac, minFee, ByteStr.empty,  System.currentTimeMillis())
      .explicitGet()
      .json()

    val dockerNodeBId = docker.stopContainer(dockerNodes.apply().last)
    val txId          = nodeA.signedBroadcast(tx).id
    docker.startContainer(dockerNodeBId)
    nodeA.waitForPeers(1)

    nodeB.ensureTxDoesntExist(txId)
    nodeA.signedBroadcast(tx)
    nodes.waitForHeightArise()
    nodeB.utxSize shouldBe 0
    nodeB.ensureTxDoesntExist(txId)

  }
}
object RebroadcastTransactionSuite {

  private val configWithRebroadcastAllowed =
    parseString("tac.synchronization.utx-synchronizer.allow-tx-rebroadcasting = true")

  private val configWithRebroadcastNotAllowed =
    parseString("tac.synchronization.utx-synchronizer.allow-tx-rebroadcasting = false")

}
