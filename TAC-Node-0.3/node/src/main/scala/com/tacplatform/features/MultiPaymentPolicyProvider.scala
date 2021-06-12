package com.tacplatform.features

import com.tacplatform.state.Blockchain

object MultiPaymentPolicyProvider {
  implicit class MultiPaymentAllowedExt(b: Blockchain) {
    def allowsMultiPayment: Boolean =
      b.activatedFeatures.contains(BlockchainFeatures.BlockV5.id)
  }
}
