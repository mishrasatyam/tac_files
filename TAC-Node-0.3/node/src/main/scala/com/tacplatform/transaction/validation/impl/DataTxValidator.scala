package com.tacplatform.transaction.validation.impl

import com.tacplatform.transaction.DataTransaction.MaxEntryCount
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.transaction.validation.{TxValidator, ValidatedV}
import com.tacplatform.transaction.{DataTransaction, TxValidationError, TxVersion}

import scala.util.Try

object DataTxValidator extends TxValidator[DataTransaction] {
  override def validate(tx: DataTransaction): ValidatedV[DataTransaction] = {
    import tx._

    V.seq(tx)(
      V.cond(data.length <= MaxEntryCount && data.forall(_.isValid(version)), TxValidationError.TooBigArray),
      V.cond(data.forall(_.key.nonEmpty), TxValidationError.EmptyDataKey),
      V.cond(data.map(_.key) == data.map(_.key).distinct, TxValidationError.DuplicatedDataKeys),
      V.fee(fee),
      V.byVersion(tx)(
        TxVersion.V1 -> { () =>
          V.seq(tx)(
            V.cond(data.forall(!_.isEmpty), GenericError("Empty data is not allowed in V1")),
            V.cond(Try(bytes().length <= DataTransaction.MaxBytes).getOrElse(false), TxValidationError.TooBigArray)
          )
        },
        TxVersion.V2 -> { () =>
          V.cond(Try(protoDataPayload.length <= DataTransaction.MaxProtoBytes).getOrElse(false), TxValidationError.TooBigArray)
        }
      )
    )
  }
}
