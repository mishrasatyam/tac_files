package com.tacplatform.lang

import com.tacplatform.lang.directives.values._
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.Types
import org.scalatest.{FreeSpec, Matchers}

class ContextVersionTest extends FreeSpec with Matchers {

  "InvokeScriptTransaction" - {
    "exist in lib version 3" in {
      val types = Types.buildTacTypes(true, V3)
      types.count(c => c.name == "InvokeScriptTransaction") shouldEqual 1
    }

    "doesn't exist in lib version 2" in {
      val types = Types.buildTacTypes(true, V2)
      types.count(c => c.name == "InvokeScriptTransaction") shouldEqual 0
    }
  }
}
