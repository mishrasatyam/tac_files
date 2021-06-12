package com.tacplatform.lang.v1

import cats.implicits._
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.directives.values._
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.repl.node.ErrorMessageEnvironment
import com.tacplatform.lang.v1.repl.node.http.{NodeConnectionSettings, WebEnvironment}
import com.tacplatform.lang.v1.traits.Environment

import scala.concurrent.Future

package object repl {
  val global: BaseGlobal = com.tacplatform.lang.Global
  val internalVarPrefixes: Set[Char] = Set('@', '$')
  val internalFuncPrefix: String = "_"

  val version = V5
  val directives: DirectiveSet = DirectiveSet(version, Account, DApp).explicitGet()

  val initialCtx: CTX[Environment] =
    CryptoContext.build(global, version).withEnvironment[Environment]  |+|
    PureContext.build(version).withEnvironment[Environment] |+|
    TacContext.build(global, directives)

  def buildEnvironment(settings: Option[NodeConnectionSettings]): Environment[Future] =
    settings.fold(ErrorMessageEnvironment[Future]("Blockchain state is unavailable from REPL"): Environment[Future])(WebEnvironment)
}
