package com.tacplatform.transaction.validation.impl

import cats.data.ValidatedNel
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.validation.TxValidator

object TransferTxValidator extends TxValidator[TransferTransaction] {
  override def validate(transaction: TransferTransaction): ValidatedNel[ValidationError, TransferTransaction] = {
    import transaction._
    V.seq(transaction)(
      V.fee(fee),
      V.positiveAmount(amount, assetId.maybeBase58Repr.getOrElse("tac")),
      V.transferAttachment(attachment),
      V.addressChainId(recipient, chainId)
    )
  }
}
