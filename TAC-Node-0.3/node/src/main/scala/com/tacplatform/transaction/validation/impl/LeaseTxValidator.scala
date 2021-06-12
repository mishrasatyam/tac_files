package com.tacplatform.transaction.validation.impl

import cats.data.ValidatedNel
import cats.implicits._
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.lease.LeaseTransaction
import com.tacplatform.transaction.validation.TxValidator
import com.tacplatform.transaction.{TxAmount, TxValidationError}

object LeaseTxValidator extends TxValidator[LeaseTransaction] {
  override def validate(tx: LeaseTransaction): ValidatedNel[ValidationError, LeaseTransaction] = {
    import tx._
    V.seq(tx)(
      V.fee(fee),
      validateAmount(tx.amount).toValidatedNel,
      V.noOverflow(amount, fee),
      V.cond(sender.toAddress != recipient, TxValidationError.ToSelf),
      V.addressChainId(recipient, chainId)
    )
  }

  def validateAmount(amount: TxAmount) =
    Either.cond(amount > 0, (), TxValidationError.NonPositiveAmount(amount, "tac"))
}
