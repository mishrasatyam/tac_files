package com.tacplatform.lang.v1.compiler

import cats.syntax.semigroup._
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.lang.Global
import com.tacplatform.lang.contract.DApp
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.directives.values.{Account, Expression, StdLibVersion, DApp => DAppType}
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.script.{ContractScript, Script}
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.traits.Environment

import scala.collection.mutable

class TestCompiler(version: StdLibVersion) {
  private lazy val baseCompilerContext =
    PureContext.build(version).withEnvironment[Environment] |+|
      CryptoContext.build(Global, version).withEnvironment[Environment]

  private lazy val compilerContext =
    (baseCompilerContext |+|
      TacContext.build(Global, DirectiveSet(version, Account, DAppType).explicitGet())).compilerContext

  private lazy val expressionCompilerContext =
    (baseCompilerContext |+|
      TacContext.build(Global, DirectiveSet(version, Account, Expression).explicitGet())).compilerContext

  def compile(script: String): Either[String, DApp] =
    ContractCompiler.compile(script, compilerContext, version)

  def compileContract(script: String): Script =
    ContractScript(version, compile(script).explicitGet()).explicitGet()

  def compileExpression(script: String): Script =
    ExprScript(version, ExpressionCompiler.compile(script, expressionCompilerContext).explicitGet()._1).explicitGet()
}

object TestCompiler {
  private val compilerByVersion = mutable.HashMap.empty[StdLibVersion, TestCompiler]
  def apply(version: StdLibVersion): TestCompiler =
    compilerByVersion.getOrElse(version, compilerByVersion.synchronized {
      compilerByVersion.getOrElseUpdate(version, new TestCompiler(version))
    })
}
