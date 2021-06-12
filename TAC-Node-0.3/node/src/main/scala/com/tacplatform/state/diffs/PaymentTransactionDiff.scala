package com.tacplatform.state.diffs

import cats.implicits._
import com.tacplatform.account.Address
import com.tacplatform.lang.ValidationError
import com.tacplatform.state.{Blockchain, Diff, LeaseBalance, Portfolio}
import com.tacplatform.transaction.PaymentTransaction
import com.tacplatform.transaction.TxValidationError.GenericError

import scala.util.{Left, Right}

object PaymentTransactionDiff {

  def apply(blockchain: Blockchain)(tx: PaymentTransaction): Either[ValidationError, Diff] = {
    val blockVersion3AfterHeight = blockchain.settings.functionalitySettings.blockVersion3AfterHeight
    if (blockchain.height > blockVersion3AfterHeight)
      Left(GenericError(s"Payment transaction is deprecated after h=$blockVersion3AfterHeight"))
    else
      Right(
        Diff(
          portfolios = Map(tx.recipient -> Portfolio(balance = tx.amount, LeaseBalance.empty, assets = Map.empty)) combine Map(
            Address.fromPublicKey(tx.sender) -> Portfolio(
              balance = -tx.amount - tx.fee,
              LeaseBalance.empty,
              assets = Map.empty
            )
          )
        )
      )
  }
}
