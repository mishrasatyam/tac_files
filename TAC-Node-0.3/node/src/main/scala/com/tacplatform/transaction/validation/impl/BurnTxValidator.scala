package com.tacplatform.transaction.validation.impl

import com.tacplatform.transaction.assets.BurnTransaction
import com.tacplatform.transaction.validation.{TxValidator, ValidatedV}

object BurnTxValidator extends TxValidator[BurnTransaction] {
  override def validate(tx: BurnTransaction): ValidatedV[BurnTransaction] = {
    import tx._
    V.seq(tx)(
      V.positiveOrZeroAmount(quantity, "assets"),
      V.fee(fee)
    )
  }
}
