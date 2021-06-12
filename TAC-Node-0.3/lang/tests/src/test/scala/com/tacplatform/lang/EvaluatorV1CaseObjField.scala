package com.tacplatform.lang

import cats.Id
import cats.kernel.Monoid
import com.tacplatform.lang.Common._
import com.tacplatform.lang.Testing._
import com.tacplatform.lang.directives.values.V1
import com.tacplatform.lang.v1.compiler.Terms._
import com.tacplatform.lang.v1.evaluator.Contextful.NoContext
import com.tacplatform.lang.v1.evaluator.ctx.EvaluationContext._
import com.tacplatform.lang.v1.evaluator.ctx._
import com.tacplatform.lang.v1.evaluator.ctx.impl.PureContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.PureContext._
import com.tacplatform.lang.v1.testing.ScriptGen
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class EvaluatorV1CaseObjField extends PropSpec with PropertyChecks with Matchers with ScriptGen with NoShrink {

  def context(p: CaseObj): EvaluationContext[NoContext, Id] =
    Monoid.combine(PureContext.build(V1).evaluationContext, sampleUnionContext(p))

  property("case custom type field access") {
    ev[CONST_LONG](
      context = context(pointAInstance),
      expr = FUNCTION_CALL(sumLong.header, List(GETTER(REF("p"), "X"), CONST_LONG(2L)))
    ) shouldBe evaluated(5)
  }

  property("case custom type field access over union") {
    def testAccess(instance: CaseObj, field: String) =
      ev[CONST_LONG](
        context = context(instance),
        expr = FUNCTION_CALL(sumLong.header, List(GETTER(REF("p"), field), CONST_LONG(2L)))
      )

    testAccess(pointAInstance, "X") shouldBe evaluated(5)
    testAccess(pointBInstance, "X") shouldBe evaluated(5)
    testAccess(pointAInstance, "YA") shouldBe evaluated(42)
    testAccess(pointBInstance, "YB") shouldBe evaluated(43)
  }
}
