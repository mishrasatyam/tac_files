package com.tacplatform.transaction.validation.impl

import cats.data.Validated
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.transaction.assets.IssueTransaction
import com.tacplatform.transaction.validation.{TxValidator, ValidatedV}
import com.tacplatform.transaction.{TxValidationError, TxVersion}

object IssueTxValidator extends TxValidator[IssueTransaction] {
  override def validate(tx: IssueTransaction): ValidatedV[IssueTransaction] = {
    def assetDecimals(decimals: Byte): ValidatedV[Byte] = {
      Validated
        .condNel(
          decimals >= 0 && decimals <= IssueTransaction.MaxAssetDecimals,
          decimals,
          TxValidationError.TooBigArray
        )
    }

    import tx._
    V.seq(tx)(
      V.positiveAmount(quantity, "assets"),
      V.assetName(tx.name),
      V.assetDescription(tx.description),
      assetDecimals(decimals),
      V.fee(fee),
      V.cond(version > TxVersion.V1 || script.isEmpty, GenericError("Script not supported")),
      V.cond(script.forall(_.isInstanceOf[ExprScript]), GenericError(s"Asset can only be assigned with Expression script, not Contract"))
    )
  }
}
