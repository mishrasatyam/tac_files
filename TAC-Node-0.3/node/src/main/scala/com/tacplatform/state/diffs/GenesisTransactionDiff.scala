package com.tacplatform.state.diffs

import com.tacplatform.lang.ValidationError
import com.tacplatform.state.{Diff, Portfolio}
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.transaction.TxValidationError.GenericError

import scala.util.{Left, Right}

object GenesisTransactionDiff {
  def apply(height: Int)(tx: GenesisTransaction): Either[ValidationError, Diff] = {
    if (height != 1) Left(GenericError(s"GenesisTransaction cannot appear in non-initial block ($height)"))
    else
      Right(Diff(portfolios = Map(tx.recipient -> Portfolio(balance = tx.amount))))
  }
}
