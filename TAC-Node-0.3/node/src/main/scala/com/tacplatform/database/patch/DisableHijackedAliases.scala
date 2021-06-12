package com.tacplatform.database.patch

import com.tacplatform.account.{AddressScheme, Alias}
import com.tacplatform.common.utils._
import com.tacplatform.database.{Keys, RW}
import com.tacplatform.state.patch.PatchLoader

case object DisableHijackedAliases {
  val height: Int = AddressScheme.current.chainId.toChar match {
    case 'W' => 1060000
    case _   => 0
  }

  def apply(rw: RW): Set[Alias] = {
    val aliases = PatchLoader.read[Set[String]](this).map(Alias.create(_).explicitGet())
    rw.put(Keys.disabledAliases, aliases)
    aliases
  }

  def revert(rw: RW): Set[Alias] = {
    rw.put(Keys.disabledAliases, Set.empty[Alias])
    Set.empty[Alias]
  }
}
