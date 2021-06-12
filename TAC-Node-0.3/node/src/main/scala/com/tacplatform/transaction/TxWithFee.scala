package com.tacplatform.transaction

import com.tacplatform.transaction.Asset.Tac

sealed trait TxWithFee {
  def fee: TxAmount
  def assetFee: (Asset, TxAmount) // TODO: Delete or rework
}

object TxWithFee {
  trait InTac extends TxWithFee {
    override def assetFee: (Asset, TxAmount) = (Tac, fee)
  }

  trait InCustomAsset extends TxWithFee {
    def feeAssetId: Asset
    override def assetFee: (Asset, TxAmount) = (feeAssetId, fee)
  }
}
