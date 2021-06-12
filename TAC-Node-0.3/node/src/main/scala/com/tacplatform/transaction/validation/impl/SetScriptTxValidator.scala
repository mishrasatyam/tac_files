package com.tacplatform.transaction.validation.impl

import com.tacplatform.transaction.smart.SetScriptTransaction
import com.tacplatform.transaction.validation.{TxValidator, _}

object SetScriptTxValidator extends TxValidator[SetScriptTransaction] {
  override def validate(tx: SetScriptTransaction): ValidatedV[SetScriptTransaction] =
    V.fee(tx.fee).map(_ => tx)
}
