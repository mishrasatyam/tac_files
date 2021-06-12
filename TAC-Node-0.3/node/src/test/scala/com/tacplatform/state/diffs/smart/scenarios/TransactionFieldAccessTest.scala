package com.tacplatform.state.diffs.smart.scenarios

import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithState
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.lang.directives.values._
import com.tacplatform.lang.utils._
import com.tacplatform.lang.v1.compiler.ExpressionCompiler
import com.tacplatform.lang.v1.parser.Parser
import com.tacplatform.state.diffs.produce
import com.tacplatform.state.diffs.smart._
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.transaction.lease.LeaseTransaction
import com.tacplatform.transaction.smart.SetScriptTransaction
import com.tacplatform.transaction.transfer._
import com.tacplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class TransactionFieldAccessTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {

  private def preconditionsTransferAndLease(
      code: String): Gen[(GenesisTransaction, SetScriptTransaction, LeaseTransaction, TransferTransaction)] = {
    val untyped = Parser.parseExpr(code).get.value
    val typed   = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untyped).explicitGet()._1
    preconditionsTransferAndLease(typed)
  }

  private val script =
    """
      |
      | match tx {
      | case ttx: TransferTransaction =>
      |       isDefined(ttx.assetId)==false
      | case _ =>
      |       false
      | }
      """.stripMargin

  property("accessing field of transaction without checking its type first results on exception") {
    forAll(preconditionsTransferAndLease(script)) {
      case ((genesis, script, lease, transfer)) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(transfer)), smartEnabledFS) { case _ => () }
        assertDiffEi(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(lease)), smartEnabledFS)(totalDiffEi =>
          totalDiffEi should produce("TransactionNotAllowedByScript"))
    }
  }
}
