package com.tacplatform.state.diffs

import com.tacplatform.lang.ValidationError
import com.tacplatform.state._
import com.tacplatform.transaction.lease._

object LeaseTransactionsDiff {
  def lease(blockchain: Blockchain)(tx: LeaseTransaction): Either[ValidationError, Diff] =
    DiffsCommon
      .processLease(blockchain, tx.amount, tx.sender, tx.recipient, tx.fee, tx.id(), tx.id())
      .map(_.copy(scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)))

  def leaseCancel(blockchain: Blockchain, time: Long)(tx: LeaseCancelTransaction): Either[ValidationError, Diff] =
    DiffsCommon
      .processLeaseCancel(blockchain, tx.sender, tx.fee, time, tx.leaseId, tx.id())
      .map(_.copy(scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)))
}
