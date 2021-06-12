package com.tacplatform.transaction.validation.impl

import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.transaction.assets.SetAssetScriptTransaction
import com.tacplatform.transaction.validation.{TxValidator, ValidatedV}

object SetAssetScriptTxValidator extends TxValidator[SetAssetScriptTransaction] {
  override def validate(tx: SetAssetScriptTransaction): ValidatedV[SetAssetScriptTransaction] = {
    import tx._
    V.seq(tx)(
      V.cond(
        script.forall(_.isInstanceOf[ExprScript]),
        GenericError(s"Asset can only be assigned with Expression script, not Contract")
      ),
      V.cond(
        script.isDefined,
        GenericError("Cannot set empty script")
      ),
      V.fee(fee)
    )
  }
}
