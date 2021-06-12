package com.tacplatform.state.diffs.invoke

import cats.implicits.catsSyntaxSemigroup
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.lang.Global
import com.tacplatform.lang.directives.values.{Account, DApp, StdLibVersion, V3}
import com.tacplatform.lang.directives.{DirectiveDictionary, DirectiveSet}
import com.tacplatform.lang.v1.evaluator.ctx.InvariableContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.traits.Environment

object CachedDAppCTX {
  val forVersion: Map[StdLibVersion, InvariableContext] =
    DirectiveDictionary[StdLibVersion].all
      .filter(_ >= V3)
      .map { version =>
        val ctx = PureContext.build(version).withEnvironment[Environment] |+|
          CryptoContext.build(Global, version).withEnvironment[Environment] |+|
          TacContext.build(Global, DirectiveSet(version, Account, DApp).explicitGet())

        (version, InvariableContext(ctx))
      }
      .toMap
}
