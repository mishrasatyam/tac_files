package com.tacplatform.it.sync.debug

import com.typesafe.config.Config
import com.tacplatform.it.{Node, NodeConfigs}
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.transactions.NodesFromDocker
import com.tacplatform.it.util._
import com.tacplatform.it.sync._
import org.scalatest.FunSuite

class DebugPortfoliosSuite extends FunSuite with NodesFromDocker {
  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .withDefault(entitiesNumber = 1)
      .buildNonConflicting()

  private def sender: Node = nodes.head

  private lazy val firstAcc  = sender.createKeyPair()
  private lazy val secondAcc = sender.createKeyPair()

  private lazy val firstAddress: String  = firstAcc.toAddress.toString
  private lazy val secondAddress: String = secondAcc.toAddress.toString

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    sender.transfer(sender.keyPair, firstAddress, 20.tac, minFee, waitForTx = true)
    sender.transfer(sender.keyPair, secondAddress, 20.tac, minFee, waitForTx = true)
  }

  test("getting a balance considering pessimistic transactions from UTX pool - changed after UTX") {
    val portfolioBefore = sender.debugPortfoliosFor(firstAddress, considerUnspent = true)
    val utxSizeBefore   = sender.utxSize

    sender.transfer(firstAcc, secondAddress, 5.tac, 5.tac)
    sender.transfer(secondAcc, firstAddress, 7.tac, 5.tac)

    sender.waitForUtxIncreased(utxSizeBefore)

    val portfolioAfter = sender.debugPortfoliosFor(firstAddress, considerUnspent = true)

    val expectedBalance = portfolioBefore.balance - 10.tac // withdraw + fee
    assert(portfolioAfter.balance == expectedBalance)

  }

  test("getting a balance without pessimistic transactions from UTX pool - not changed after UTX") {
    nodes.waitForHeightArise()

    val portfolioBefore = sender.debugPortfoliosFor(firstAddress, considerUnspent = false)
    val utxSizeBefore   = sender.utxSize

    sender.transfer(firstAcc, secondAddress, 5.tac, fee = 5.tac)
    sender.waitForUtxIncreased(utxSizeBefore)

    val portfolioAfter = sender.debugPortfoliosFor(firstAddress, considerUnspent = false)
    assert(portfolioAfter.balance == portfolioBefore.balance)
  }
}
