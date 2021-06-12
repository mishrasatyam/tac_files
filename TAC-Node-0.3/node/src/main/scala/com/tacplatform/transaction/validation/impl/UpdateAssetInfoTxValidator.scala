package com.tacplatform.transaction.validation.impl

import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction.assets.UpdateAssetInfoTransaction
import com.tacplatform.transaction.validation.{TxValidator, ValidatedV}
import com.tacplatform.transaction.{Asset, TxValidationError}
import com.tacplatform.utils.StringBytes

object UpdateAssetInfoTxValidator extends TxValidator[UpdateAssetInfoTransaction] {
  override def validate(tx: UpdateAssetInfoTransaction): ValidatedV[UpdateAssetInfoTransaction] =
    V.seq(tx)(
      V.cond(UpdateAssetInfoTransaction.supportedVersions(tx.version), TxValidationError.UnsupportedVersion(tx.version)),
      V.fee(tx.feeAmount),
      V.asset[IssuedAsset](tx.assetId),
      V.asset[Asset](tx.feeAsset),
      V.assetName(tx.name.toByteString),
      V.assetDescription(tx.description.toByteString)
    )
}
