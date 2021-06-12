package com.tacplatform

import com.tacplatform.common.state.ByteStr
import com.tacplatform.settings.WalletSettings
import com.tacplatform.wallet.Wallet

trait TestWallet {
  protected val testWallet: Wallet = TestWallet.instance
}

object TestWallet {
  private[TestWallet] lazy val instance = Wallet(WalletSettings(None, Some("123"), Some(ByteStr.empty)))
}
