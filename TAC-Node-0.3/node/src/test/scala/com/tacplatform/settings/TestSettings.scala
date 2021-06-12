package com.tacplatform.settings

import com.typesafe.config.ConfigFactory
import com.tacplatform.features.BlockchainFeatures

object TestSettings {
  val Default: TacSettings = TacSettings.fromRootConfig(ConfigFactory.load())

  implicit class TacSettingsExt(val ws: TacSettings) extends AnyVal {
    def withFunctionalitySettings(fs: FunctionalitySettings): TacSettings =
      ws.copy(blockchainSettings = ws.blockchainSettings.copy(functionalitySettings = fs))

    def withNG: TacSettings =
      ws.withFunctionalitySettings(
        ws.blockchainSettings.functionalitySettings.copy(
          blockVersion3AfterHeight = 0,
          preActivatedFeatures = ws.blockchainSettings.functionalitySettings.preActivatedFeatures ++ Map(BlockchainFeatures.NG.id -> 0)
        )
      )
  }
}
