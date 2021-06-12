package com.tacplatform.api
import com.tacplatform.account.Address
import com.tacplatform.api.common.CommonTransactionsApi.TransactionMeta
import com.tacplatform.common.state.ByteStr
import com.tacplatform.database.{DBExt, Keys}
import com.tacplatform.state.{Diff, Height}
import com.tacplatform.transaction.CreateAliasTransaction
import com.tacplatform.transaction.lease.LeaseTransaction
import monix.reactive.Observable
import org.iq80.leveldb.DB

package object common extends BalanceDistribution with AddressTransactions {
  def aliasesOfAddress(db: DB, maybeDiff: => Option[(Height, Diff)], address: Address): Observable[(Height, CreateAliasTransaction)] = {
    val disabledAliases = db.get(Keys.disabledAliases)
    addressTransactions(db, maybeDiff, address, Some(address), Set(CreateAliasTransaction.typeId), None)
      .collect {
        case TransactionMeta(height, cat: CreateAliasTransaction, true) if disabledAliases.isEmpty || !disabledAliases(cat.alias) => height -> cat
      }
  }

  def activeLeases(
      db: DB,
      maybeDiff: Option[(Height, Diff)],
      address: Address,
      leaseIsActive: ByteStr => Boolean
  ): Observable[(Height, LeaseTransaction)] =
    addressTransactions(db, maybeDiff, address, None, Set(LeaseTransaction.typeId), None)
      .collect { case TransactionMeta(h, lt: LeaseTransaction, true) if leaseIsActive(lt.id()) => h -> lt }
}
