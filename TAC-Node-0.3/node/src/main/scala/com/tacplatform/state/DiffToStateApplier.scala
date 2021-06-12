package com.tacplatform.state

import cats.syntax.monoid._
import com.tacplatform.account.Address
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.Asset.Tac

/**
  * A set of functions that apply diff
  * to the blockchain and return new
  * state values (only changed ones)
  */
object DiffToStateApplier {
  case class PortfolioUpdates(
      balances: Map[Address, Map[Asset, Long]],
      leases: Map[Address, LeaseBalance]
  )

  def portfolios(blockchain: Blockchain, diff: Diff): PortfolioUpdates = {
    val balances = Map.newBuilder[Address, Map[Asset, Long]]
    val leases   = Map.newBuilder[Address, LeaseBalance]

    for ((address, portfolioDiff) <- diff.portfolios) {
      // balances for address
      val bs = Map.newBuilder[Asset, Long]

      if (portfolioDiff.balance != 0) {
        bs += Tac -> (blockchain.balance(address, Tac) + portfolioDiff.balance)
      }

      portfolioDiff.assets.collect {
        case (asset, balanceDiff) if balanceDiff != 0 =>
          bs += asset -> (blockchain.balance(address, asset) + balanceDiff)
      }

      balances += address -> bs.result()

      // leases
      if (portfolioDiff.lease != LeaseBalance.empty) {
        leases += address -> blockchain.leaseBalance(address).combine(portfolioDiff.lease)
      }
    }

    PortfolioUpdates(balances.result(), leases.result())
  }
}
