package com.tacplatform.state.diffs.smart.performance

import com.tacplatform.account.{KeyPair, PublicKey}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithState
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.lang.directives.values._
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.utils._
import com.tacplatform.lang.v1.compiler.ExpressionCompiler
import com.tacplatform.lang.v1.compiler.Terms._
import com.tacplatform.lang.v1.parser.Parser
import com.tacplatform.metrics.Instrumented
import com.tacplatform.state.diffs._
import com.tacplatform.state.diffs.smart._
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.transaction.transfer._
import com.tacplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class SigVerifyPerformanceTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {

  private val AmtOfTxs = 10000

  private def simpleSendGen(from: KeyPair, to: PublicKey, ts: Long): Gen[TransferTransaction] =
    for {
      amt <- smallFeeGen
      fee <- smallFeeGen
    } yield TransferTransaction.selfSigned(1.toByte, from, to.toAddress, Tac, amt, Tac, fee, ByteStr.empty,  ts).explicitGet()

  private def scriptedSendGen(from: KeyPair, to: PublicKey, ts: Long): Gen[TransferTransaction] =
    for {
      amt <- smallFeeGen
      fee <- smallFeeGen
    } yield TransferTransaction.selfSigned(2.toByte, from, to.toAddress, Tac, amt, Tac, fee, ByteStr.empty,  ts).explicitGet()

  private def differentTransfers(typed: EXPR) =
    for {
      master    <- accountGen
      recipient <- accountGen
      ts        <- positiveIntGen
      amt       <- smallFeeGen
      fee       <- smallFeeGen
      genesis = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      setScript <- selfSignedSetScriptTransactionGenP(master, ExprScript(typed).explicitGet())
      transfer       = simpleSendGen(master, recipient.publicKey, ts)
      scriptTransfer = scriptedSendGen(master, recipient.publicKey, ts)
      transfers       <- Gen.listOfN(AmtOfTxs, transfer)
      scriptTransfers <- Gen.listOfN(AmtOfTxs, scriptTransfer)
    } yield (genesis, setScript, transfers, scriptTransfers)

  ignore("parallel native signature verification vs sequential scripted signature verification") {
    val textScript    = "sigVerify(tx.bodyBytes,tx.proofs[0],tx.senderPk)"
    val untypedScript = Parser.parseExpr(textScript).get.value
    val typedScript   = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untypedScript).explicitGet()._1

    forAll(differentTransfers(typedScript)) {
      case (gen, setScript, transfers, scriptTransfers) =>
        def simpleCheck(): Unit = assertDiffAndState(Seq(TestBlock.create(Seq(gen))), TestBlock.create(transfers), smartEnabledFS) { case _ => }
        def scriptedCheck(): Unit =
          assertDiffAndState(Seq(TestBlock.create(Seq(gen, setScript))), TestBlock.create(scriptTransfers), smartEnabledFS) {
            case _ =>
          }

        val simeplCheckTime   = Instrumented.withTimeMillis(simpleCheck())._2
        val scriptedCheckTime = Instrumented.withTimeMillis(scriptedCheck())._2
        println(s"[parallel] simple check time: $simeplCheckTime ms,\t [seqential] scripted check time: $scriptedCheckTime ms")
    }

  }
}
