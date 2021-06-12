package com.tacplatform.features

import com.tacplatform.state.Blockchain

object FunctionCallPolicyProvider {
  implicit class MultiPaymentAllowedExt(b: Blockchain) {
    def callableListArgumentsAllowed: Boolean =
      b.activatedFeatures.contains(BlockchainFeatures.BlockV5.id)
  }
}
