package com.tacplatform.transaction

import com.tacplatform.account.Address

case class AssetAcc(account: Address, assetId: Option[Asset])
