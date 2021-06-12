package com.tacplatform.state.patch

import com.tacplatform.account.{Address, AddressScheme}
import com.tacplatform.common.utils._
import com.tacplatform.state.patch.CancelAllLeases.CancelledLeases
import com.tacplatform.state.{Diff, Portfolio}

case object CancelLeaseOverflow extends DiffPatchFactory {
  val height: Int = AddressScheme.current.chainId.toChar match {
    case 'W' => 795000
    case _   => 0
  }

  def apply(): Diff = {
    val patch = PatchLoader.read[CancelledLeases](this)
    val pfs = patch.balances.map {
      case (address, lb) =>
        Address.fromString(address).explicitGet() -> Portfolio(lease = lb)
    }
    Diff.empty.copy(portfolios =  pfs, leaseState = patch.leaseStates)
  }
}
