package com.tacplatform.state.diffs

import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.settings.{FunctionalitySettings, TestFunctionalitySettings}

package object smart {
  val smartEnabledFS: FunctionalitySettings =
    TestFunctionalitySettings.Enabled.copy(
      preActivatedFeatures = Map(
        BlockchainFeatures.SmartAccounts.id   -> 0,
        BlockchainFeatures.SmartAssets.id     -> 0,
        BlockchainFeatures.DataTransaction.id -> 0,
        BlockchainFeatures.Ride4DApps.id      -> 0
      )
    )
}
