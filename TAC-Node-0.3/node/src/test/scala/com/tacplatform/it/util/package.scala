package com.tacplatform.it

import com.tacplatform.account.{Address, AddressOrAlias, Alias}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.v1.traits.domain.Recipient
import com.tacplatform.settings.Constants

package object util {
  implicit class DoubleExt(val d: Double) extends AnyVal {
    def tac: Long = (BigDecimal(d) * Constants.UnitsInTac).toLong
  }

  implicit class AddressOrAliasExt(val a: AddressOrAlias) extends AnyVal {
    def toRide: Recipient =
      a match {
        case address: Address => Recipient.Address(ByteStr(address.bytes))
        case alias: Alias     => Recipient.Alias(alias.name)
        case _                => ???
      }
  }
}
