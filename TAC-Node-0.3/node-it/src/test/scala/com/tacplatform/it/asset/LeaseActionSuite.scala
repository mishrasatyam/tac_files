package com.tacplatform.it.asset

import com.typesafe.config.Config
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.it.NodeConfigs
import com.tacplatform.it.NodeConfigs.Default
import com.tacplatform.it.api.LeaseInfo
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.lang.v1.compiler.Terms.CONST_BYTESTR
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.lang.v1.traits.domain.{Lease, Recipient}
import com.tacplatform.transaction.TxVersion
import com.tacplatform.transaction.smart.script.ScriptCompiler

class LeaseActionSuite extends BaseTransactionSuite {
  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs
      .Builder(Default, 2, Seq.empty)
      .overrideBase(_.preactivatedFeatures((BlockchainFeatures.SynchronousCalls.id, 1)))
      .buildNonConflicting()

  private def compile(script: String): String =
    ScriptCompiler.compile(script, ScriptEstimatorV3).explicitGet()._1.bytes().base64

  private val dAppLeaseAmount     = 123
  private val txLeaseAmount       = 456
  private lazy val dAppAcc        = firstKeyPair
  private lazy val dAppAddress    = firstAddress
  private lazy val invoker        = secondKeyPair
  private lazy val invokerAddress = secondAddress

  test("set script") {
    val dApp = compile(
      s"""
       |  {-# STDLIB_VERSION 5 #-}
       |  {-# CONTENT_TYPE DAPP #-}
       |  {-# SCRIPT_TYPE ACCOUNT #-}
       |
       |  @Callable(i)
       |  func lease() = {
       |    [
       |      Lease(i.caller, $dAppLeaseAmount)
       |    ]
       |  }
       |
       |  @Callable(i)
       |  func leaseCancel(leaseId: ByteVector) = {
       |    [
       |      LeaseCancel(leaseId)
       |    ]
       |  }
     """.stripMargin
    )
    sender.setScript(dAppAcc, Some(dApp), waitForTx = true)
  }

  test("active leases") {
    val leaseTxId     = sender.lease(dAppAcc, invokerAddress, txLeaseAmount, smartMinFee, TxVersion.V2, waitForTx = true).id
    val leaseTxHeight = sender.transactionStatus(leaseTxId).height.get

    val invokeId     = sender.invokeScript(invoker, dAppAddress, Some("lease"), Nil, fee = invokeFee, waitForTx = true)._1.id
    val invokeHeight = sender.transactionStatus(invokeId).height.get

    val recipient     = Recipient.Address(ByteStr.decodeBase58(invokerAddress).get)
    val leaseActionId = Lease.calculateId(Lease(recipient, dAppLeaseAmount, 0), ByteStr.decodeBase58(invokeId).get).toString

    sender.activeLeases(dAppAddress) should contain theSameElementsAs Seq(
      LeaseInfo(leaseTxId, leaseTxId, dAppAddress, invokerAddress, txLeaseAmount, leaseTxHeight),
      LeaseInfo(leaseActionId, invokeId, dAppAddress, invokerAddress, dAppLeaseAmount, invokeHeight)
    )

    val leaseTxIdParam = List(CONST_BYTESTR(ByteStr.decodeBase58(leaseTxId).get).explicitGet())
    sender.invokeScript(dAppAcc, dAppAddress, Some("leaseCancel"), leaseTxIdParam, fee = invokeFee, waitForTx = true)
    sender.activeLeases(dAppAddress) shouldBe Seq(
      LeaseInfo(leaseActionId, invokeId, dAppAddress, invokerAddress, dAppLeaseAmount, invokeHeight)
    )
  }
}
