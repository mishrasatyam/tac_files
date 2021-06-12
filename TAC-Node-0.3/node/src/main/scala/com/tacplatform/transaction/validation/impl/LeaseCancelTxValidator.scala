package com.tacplatform.transaction.validation.impl

import cats.data.ValidatedNel
import cats.implicits._
import com.tacplatform.common.state.ByteStr
import com.tacplatform.crypto
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.transaction.lease.LeaseCancelTransaction
import com.tacplatform.transaction.validation.TxValidator

object LeaseCancelTxValidator extends TxValidator[LeaseCancelTransaction] {
  override def validate(tx: LeaseCancelTransaction): ValidatedNel[ValidationError, LeaseCancelTransaction] = {
    import tx._
    V.seq(tx)(
      V.fee(fee),
      checkLeaseId(leaseId).toValidatedNel
    )
  }

  def checkLeaseId(leaseId: ByteStr): Either[GenericError, Unit] =
    Either.cond(
      leaseId.arr.length == crypto.DigestLength,
      (),
      GenericError(s"Lease id=$leaseId has invalid length = ${leaseId.arr.length} byte(s) while expecting ${crypto.DigestLength}")
    )
}
