package com.tacplatform.features

import com.tacplatform.state.Blockchain

object ScriptTransferValidationProvider {
  implicit class PassCorrectAssetIdExt(b: Blockchain) {
    def passCorrectAssetId: Boolean =
      b.isFeatureActivated(BlockchainFeatures.BlockV5)
  }
}
