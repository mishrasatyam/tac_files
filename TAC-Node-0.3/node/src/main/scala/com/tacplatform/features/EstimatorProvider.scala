package com.tacplatform.features

import com.tacplatform.features.BlockchainFeatures.{BlockReward, BlockV5, SynchronousCalls}
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.lang.v1.estimator.{ScriptEstimator, ScriptEstimatorV1}
import com.tacplatform.settings.TacSettings
import com.tacplatform.state.Blockchain

object EstimatorProvider {
  implicit class EstimatorBlockchainExt(b: Blockchain) {
    def estimator: ScriptEstimator =
      if (b.isFeatureActivated(BlockV5)) ScriptEstimatorV3
      else if (b.isFeatureActivated(BlockReward)) ScriptEstimatorV2
      else ScriptEstimatorV1

    def storeEvaluatedComplexity: Boolean =
      b.isFeatureActivated(SynchronousCalls)
  }

  implicit class EstimatorTacSettingsExt(ws: TacSettings) {
    def estimator: ScriptEstimator =
      if (ws.featuresSettings.supported.contains(BlockV5.id)) ScriptEstimatorV3
      else if (ws.featuresSettings.supported.contains(BlockReward.id)) ScriptEstimatorV2
      else ScriptEstimatorV1
  }
}
