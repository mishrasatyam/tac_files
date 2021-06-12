package com.tacplatform.features

import com.tacplatform.state.Blockchain

object InvokeScriptSelfPaymentPolicyProvider {
  implicit class SelfPaymentPolicyBlockchainExt(b: Blockchain) {
    def disallowSelfPayment: Boolean =
      b.isFeatureActivated(BlockchainFeatures.BlockV5)
  }
}
