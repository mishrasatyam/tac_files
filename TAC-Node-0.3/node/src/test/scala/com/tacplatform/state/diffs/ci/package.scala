package com.tacplatform.state.diffs

import cats.implicits._
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.lang.Global
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.directives.values.{Expression, ScriptType, StdLibVersion}
import com.tacplatform.lang.script.Script
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.v1.compiler.ExpressionCompiler
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.parser.Parser
import com.tacplatform.lang.v1.traits.Environment
import com.tacplatform.state.diffs.FeeValidation._
import com.tacplatform.transaction.assets.IssueTransaction
import com.tacplatform.transaction.smart.InvokeScriptTransaction
import org.scalacheck.Gen

package object ci {
  private val invokeFee = FeeUnit * FeeConstants(InvokeScriptTransaction.typeId)

  def ciFee(sc: Int = 0, nonNftIssue: Int = 0): Gen[Long] =
    Gen.choose(
      invokeFee + sc * ScriptExtraFee + nonNftIssue * FeeConstants(IssueTransaction.typeId) * FeeUnit,
      invokeFee + (sc + 1) * ScriptExtraFee - 1 + nonNftIssue * FeeConstants(IssueTransaction.typeId) * FeeUnit
    )

  def compileExpr(script: String, version: StdLibVersion, scriptType: ScriptType): Script =
    ExprScript(
      version,
      ExpressionCompiler(
        (PureContext.build(version).withEnvironment[Environment] |+|
          CryptoContext.build(Global, version).withEnvironment[Environment] |+|
          TacContext.build(
            Global,
            DirectiveSet(version, scriptType, Expression).explicitGet()
          )).compilerContext,
        Parser.parseExpr(script).get.value
      ).explicitGet()._1
    ).explicitGet()
}
