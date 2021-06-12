package com.tacplatform.utils

import cats.Id
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.directives.values.V3
import com.tacplatform.lang.utils._
import com.tacplatform.lang.v1.compiler.Terms.{FUNCTION_CALL, TRUE}
import com.tacplatform.lang.v1.compiler.Types.BOOLEAN
import com.tacplatform.lang.v1.evaluator.ctx.{EvaluationContext, UserFunction}
import com.tacplatform.lang.v1.traits.Environment
import com.tacplatform.state.diffs.smart.predef.chainId
import com.tacplatform.common.state.ByteStr
import com.tacplatform.transaction.smart.TacEnvironment
import monix.eval.Coeval
import org.scalatest.{FreeSpec, Matchers}

class UtilsSpecification extends FreeSpec with Matchers {
  private val environment = new TacEnvironment(chainId, Coeval(???), null, EmptyBlockchain, null, DirectiveSet.contractDirectiveSet, ByteStr.empty)

  "estimate()" - {
    "handles functions that depend on each other" in {
      val callee = UserFunction[Environment]("callee", 0, BOOLEAN)(TRUE)
      val caller = UserFunction[Environment]("caller", 0, BOOLEAN)(FUNCTION_CALL(callee.header, List.empty))
      val ctx = EvaluationContext.build[Id, Environment](
        environment,
        typeDefs = Map.empty,
        letDefs = Map.empty,
        functions = Seq(caller, callee)
      )
      estimate(V3, ctx).size shouldBe 2
    }
  }
}
