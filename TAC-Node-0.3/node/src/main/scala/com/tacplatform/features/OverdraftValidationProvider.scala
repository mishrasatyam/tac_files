package com.tacplatform.features

import com.tacplatform.state.Blockchain

object OverdraftValidationProvider {
  implicit class ConsistentOverdraftExt(b: Blockchain) {
    def useCorrectPaymentCheck: Boolean =
      b.activatedFeatures.contains(BlockchainFeatures.BlockV5.id)
  }
}
