package com.tacplatform.db
import com.typesafe.config.ConfigFactory
import com.tacplatform.settings.TacSettings

trait DBCacheSettings {
  lazy val dbSettings = TacSettings.fromRootConfig(ConfigFactory.load()).dbSettings
  lazy val maxCacheSize: Int = dbSettings.maxCacheSize
}
