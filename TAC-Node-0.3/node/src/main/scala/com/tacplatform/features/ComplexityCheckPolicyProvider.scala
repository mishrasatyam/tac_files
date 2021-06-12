package com.tacplatform.features

import com.tacplatform.state.Blockchain

object ComplexityCheckPolicyProvider {
  implicit class VerifierComplexityCheckExt(b: Blockchain) {
    def useReducedVerifierComplexityLimit: Boolean =
      b.activatedFeatures.contains(BlockchainFeatures.BlockV5.id)
  }
}
