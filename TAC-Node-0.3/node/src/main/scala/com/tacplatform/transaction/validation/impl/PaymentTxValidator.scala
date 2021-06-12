package com.tacplatform.transaction.validation.impl

import cats.data.ValidatedNel
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.PaymentTransaction
import com.tacplatform.transaction.validation.TxValidator

object PaymentTxValidator extends TxValidator[PaymentTransaction] {
  override def validate(transaction: PaymentTransaction): ValidatedNel[ValidationError, PaymentTransaction] = {
    import transaction._
    V.seq(transaction)(
      V.fee(fee),
      V.positiveAmount(amount, "tac"),
      V.noOverflow(fee, amount),
      V.addressChainId(recipient, chainId)
    )
  }
}
