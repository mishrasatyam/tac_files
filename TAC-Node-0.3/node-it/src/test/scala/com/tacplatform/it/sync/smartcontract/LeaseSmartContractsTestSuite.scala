package com.tacplatform.it.sync.smartcontract

import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.crypto
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync.{minFee, setScriptFee, transferAmount}
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.it.util._
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.transaction.Proofs
import com.tacplatform.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.tacplatform.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class LeaseSmartContractsTestSuite extends BaseTransactionSuite with CancelAfterFailure {
  private def acc0 = firstKeyPair
  private def acc1 = secondKeyPair
  private def acc2 = thirdKeyPair

  test("set contract, make leasing and cancel leasing") {
    val (balance1, eff1) = miner.accountBalances(acc0.toAddress.toString)
    val (balance2, eff2) = miner.accountBalances(thirdKeyPair.toAddress.toString)

    sender.transfer(sender.keyPair, acc0.toAddress.toString, 10 * transferAmount, minFee, waitForTx = true).id

    miner.assertBalances(firstAddress, balance1 + 10 * transferAmount, eff1 + 10 * transferAmount)

    val scriptText = s"""
        let pkA = base58'${acc0.publicKey}'
        let pkB = base58'${acc1.publicKey}'
        let pkC = base58'${acc2.publicKey}'

        match tx {
          case ltx: LeaseTransaction => sigVerify(ltx.bodyBytes,ltx.proofs[0],pkA) && sigVerify(ltx.bodyBytes,ltx.proofs[2],pkC)
          case lctx : LeaseCancelTransaction => sigVerify(lctx.bodyBytes,lctx.proofs[1],pkA) && sigVerify(lctx.bodyBytes,lctx.proofs[2],pkB)
          case _ => false
        }
        """.stripMargin

    val script = ScriptCompiler(scriptText, isAssetScript = false, ScriptEstimatorV2).explicitGet()._1.bytes().base64
    sender.setScript(acc0, Some(script), setScriptFee, waitForTx = true).id

    val unsignedLeasing =
      LeaseTransaction
        .create(
          2.toByte,
          acc0.publicKey,
          acc2.toAddress,
          transferAmount,
          minFee + 0.2.tac,
          System.currentTimeMillis(),
          Proofs.empty
        )
        .explicitGet()

    val sigLeasingA = crypto.sign(acc0.privateKey, unsignedLeasing.bodyBytes())
    val sigLeasingC = crypto.sign(acc2.privateKey, unsignedLeasing.bodyBytes())

    val signedLeasing =
      unsignedLeasing.copy(proofs = Proofs(Seq(sigLeasingA, ByteStr.empty, sigLeasingC)))

    val leasingId =
      sender.signedBroadcast(signedLeasing.json(), waitForTx = true).id

    miner.assertBalances(
      firstAddress,
      balance1 + 10 * transferAmount - (minFee + setScriptFee + 0.2.tac),
      eff1 + 9 * transferAmount - (minFee + setScriptFee + 0.2.tac)
    )
    miner.assertBalances(thirdAddress, balance2, eff2 + transferAmount)

    val unsignedCancelLeasing =
      LeaseCancelTransaction
        .create(
          version = 2.toByte,
          sender = acc0.publicKey,
          leaseId = ByteStr.decodeBase58(leasingId).get,
          fee = minFee + 0.2.tac,
          timestamp = System.currentTimeMillis(),
          proofs = Proofs.empty
        )
        .explicitGet()

    val sigLeasingCancelA = crypto.sign(acc0.privateKey, unsignedCancelLeasing.bodyBytes())
    val sigLeasingCancelB = crypto.sign(acc1.privateKey, unsignedCancelLeasing.bodyBytes())

    val signedLeasingCancel =
      unsignedCancelLeasing.copy(proofs = Proofs(Seq(ByteStr.empty, sigLeasingCancelA, sigLeasingCancelB)))

    sender.signedBroadcast(signedLeasingCancel.json(), waitForTx = true).id

    miner.assertBalances(
      firstAddress,
      balance1 + 10 * transferAmount - (2 * minFee + setScriptFee + 2 * 0.2.tac),
      eff1 + 10 * transferAmount - (2 * minFee + setScriptFee + 2 * 0.2.tac)
    )
    miner.assertBalances(thirdAddress, balance2, eff2)

  }
}
