package com.tacplatform.settings

trait CustomValueReaders {
  implicit val networkSettingsValueReader = NetworkSettings.valueReader
  implicit val blockchainSettingsValueReader = BlockchainSettings.valueReader
}
