package com.tacplatform.utils.doc

import cats.implicits._
import com.tacplatform.lang.Global
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.v1.CTX
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.traits.Environment

object RideFullContext {
  def build(ds: DirectiveSet): CTX[Environment] = {
    val tacCtx  = TacContext.build(Global,ds)
    val cryptoCtx = CryptoContext.build(Global, ds.stdLibVersion).withEnvironment[Environment]
    val pureCtx = PureContext.build(ds.stdLibVersion).withEnvironment[Environment]
    pureCtx |+| cryptoCtx |+| tacCtx
  }
}
