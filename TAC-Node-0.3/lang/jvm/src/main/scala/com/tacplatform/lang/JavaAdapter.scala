package com.tacplatform.lang

import cats.kernel.Monoid
import com.tacplatform.lang.directives.values.V2
import com.tacplatform.lang.v1.compiler.ExpressionCompiler
import com.tacplatform.lang.v1.compiler.Terms.EXPR
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}

object JavaAdapter {
  private val version = V2

  lazy val ctx =
    Monoid.combineAll(
      Seq(
        CryptoContext.compilerContext(Global, version),
        TacContext.build(Global, ???).compilerContext,
        PureContext.build(version).compilerContext
      ))

  def compile(input: String): EXPR = {
    ExpressionCompiler
      .compileBoolean(input, ctx)
      .fold(
        error => throw new IllegalArgumentException(error),
        res => res
      )
  }
}
