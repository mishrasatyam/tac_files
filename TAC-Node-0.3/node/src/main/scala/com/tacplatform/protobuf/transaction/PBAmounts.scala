package com.tacplatform.protobuf.transaction

import com.google.protobuf.ByteString
import com.tacplatform.protobuf._
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}

object PBAmounts {
  def toPBAssetId(asset: Asset): ByteString = asset match {
    case Asset.IssuedAsset(id) => id.toByteString
    case Asset.Tac           => ByteString.EMPTY
  }

  def toVanillaAssetId(byteStr: ByteString): Asset = {
    if (byteStr.isEmpty) Tac
    else IssuedAsset(byteStr.toByteStr)
  }

  def fromAssetAndAmount(asset: Asset, amount: Long): Amount =
    Amount(toPBAssetId(asset), amount)

  def toAssetAndAmount(value: Amount): (Asset, Long) =
    (toVanillaAssetId(value.assetId), value.amount)
}
