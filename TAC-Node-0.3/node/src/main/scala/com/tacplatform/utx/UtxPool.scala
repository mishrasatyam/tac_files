package com.tacplatform.utx

import com.tacplatform.account.Address
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.mining.MultiDimensionalMiningConstraint
import com.tacplatform.state.Portfolio
import com.tacplatform.transaction._
import com.tacplatform.transaction.smart.script.trace.TracedResult
import com.tacplatform.utx.UtxPool.PackStrategy

import scala.concurrent.duration.FiniteDuration

trait UtxPool extends AutoCloseable {
  def putIfNew(tx: Transaction, forceValidate: Boolean = false): TracedResult[ValidationError, Boolean]
  def removeAll(txs: Iterable[Transaction]): Unit
  def spendableBalance(addr: Address, assetId: Asset): Long
  def pessimisticPortfolio(addr: Address): Portfolio
  def all: Seq[Transaction]
  def size: Int
  def transactionById(transactionId: ByteStr): Option[Transaction]
  def packUnconfirmed(
      rest: MultiDimensionalMiningConstraint,
      strategy: PackStrategy = PackStrategy.Unlimited,
      cancelled: () => Boolean = () => false
  ): (Option[Seq[Transaction]], MultiDimensionalMiningConstraint)
}

object UtxPool {
  sealed trait PackStrategy
  object PackStrategy {
    case class Limit(time: FiniteDuration)    extends PackStrategy
    case class Estimate(time: FiniteDuration) extends PackStrategy
    case object Unlimited                     extends PackStrategy
  }
}
