package com.tacplatform.transaction.smart.script.estimator

import cats.kernel.Monoid
import com.tacplatform.account.{Address, PublicKey}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.lang.directives.values._
import com.tacplatform.lang.directives.{DirectiveDictionary, DirectiveSet}
import com.tacplatform.lang.v1.compiler.{ExpressionCompiler, _}
import com.tacplatform.lang.v1.estimator.ScriptEstimator
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.parser.Expressions.EXPR
import com.tacplatform.lang.v1.parser.Parser
import com.tacplatform.lang.v1.testing.TypedScriptGen
import com.tacplatform.lang.v1.traits.Environment
import com.tacplatform.lang.v1.{CTX, FunctionHeader}
import com.tacplatform.lang.{Global, utils}
import com.tacplatform.state.diffs.smart.predef.{chainId, scriptWithAllV1Functions}
import com.tacplatform.state.{BinaryDataEntry, BooleanDataEntry, IntegerDataEntry, StringDataEntry}
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.smart.TacEnvironment
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{DataTransaction, Proofs}
import com.tacplatform.utils.EmptyBlockchain
import monix.eval.Coeval
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class FunctionComplexityTest(estimator: ScriptEstimator) extends PropSpec with PropertyChecks with Matchers with TypedScriptGen {
  private val environment = new TacEnvironment(chainId, Coeval(???), null, EmptyBlockchain, null, DirectiveSet.contractDirectiveSet, ByteStr.empty)

  private def estimate(
      expr: Terms.EXPR,
      ctx: CTX[Environment],
      funcCosts: Map[FunctionHeader, Coeval[Long]]
  ): Either[String, Long] =
    estimator(ctx.evaluationContext(environment).letDefs.keySet, funcCosts, expr)

  private def ctx(version: StdLibVersion): CTX[Environment] = {
    utils.functionCosts(version)
    Monoid
      .combineAll(
        Seq(
          PureContext.build(version).withEnvironment[Environment],
          CryptoContext.build(Global, version).withEnvironment[Environment],
          TacContext.build(
            Global,
            DirectiveSet(version, Account, Expression).explicitGet()
          )
        )
      )
  }

  private def getAllFuncExpression(version: StdLibVersion): EXPR = {
    val entry1 = IntegerDataEntry("int", 24)
    val entry2 = BooleanDataEntry("bool", true)
    val entry3 = BinaryDataEntry("blob", ByteStr.decodeBase64("YWxpY2U=").get)
    val entry4 = StringDataEntry("str", "test")

    val dtx = DataTransaction
      .create(
        1.toByte,
        PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
        List(entry1, entry2, entry3, entry4),
        100000,
        1526911531530L,
        Proofs(Seq(ByteStr.decodeBase58("32mNYSefBTrkVngG5REkmmGAVv69ZvNhpbegmnqDReMTmXNyYqbECPgHgXrX2UwyKGLFS45j7xDFyPXjF8jcfw94").get))
      )
      .explicitGet()

    val recipient = Address.fromString("3My3KZgFQ3CrVHgz6vGRt8687sH4oAA1qp8").explicitGet()
    val ttx = TransferTransaction(
      2.toByte,
      PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
      recipient,
      Tac,
      100000000,
      Tac,
      100000000,
      ByteStr.decodeBase58("4t2Xazb2SX").get,
      1526641218066L,
      Proofs(Seq(ByteStr.decodeBase58("4bfDaqBcnK3hT8ywFEFndxtS1DTSYfncUqd4s5Vyaa66PZHawtC73rDswUur6QZu5RpqM7L9NFgBHT1vhCoox4vi").get)),
      recipient.chainId
    )

    val script = scriptWithAllV1Functions(dtx, ttx)
    val adaptedScript =
      if (version == V3) script.replace("transactionById", "transferTransactionById")
      else script

    Parser.parseExpr(adaptedScript).get.value
  }

  property("function complexities are correctly defined ") {
    DirectiveDictionary[StdLibVersion].all
      .foreach { version =>
        ctx(version).functions
          .foreach { function =>
            noException should be thrownBy
              DirectiveDictionary[StdLibVersion].all
                .filter(_ >= version)
                .map(function.costByLibVersion)
          }
      }
  }

  property("estimate script with all functions") {
    def check(version: StdLibVersion, expectedCost: Int) = {
      val expr = ExpressionCompiler(ctx(version).compilerContext, getAllFuncExpression(version)).explicitGet()._1
      estimate(expr, ctx(version), utils.functionCosts(version)) shouldBe Right(expectedCost)
    }

    check(V1, 2317)
    check(V2, 2317)
    check(V3, 1882)
  }
}
