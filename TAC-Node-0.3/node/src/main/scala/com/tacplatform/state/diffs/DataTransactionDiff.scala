package com.tacplatform.state.diffs

import com.tacplatform.lang.ValidationError
import com.tacplatform.state._
import com.tacplatform.transaction.DataTransaction

object DataTransactionDiff {

  def apply(blockchain: Blockchain)(tx: DataTransaction): Either[ValidationError, Diff] = {
    val sender = tx.sender.toAddress
    Right(
      Diff(
        portfolios = Map(sender -> Portfolio(-tx.fee, LeaseBalance.empty, Map.empty)),
        accountData = Map(sender -> AccountDataInfo(tx.data.map(item => item.key -> item).toMap)),
        scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)
      )
    )
  }
}
