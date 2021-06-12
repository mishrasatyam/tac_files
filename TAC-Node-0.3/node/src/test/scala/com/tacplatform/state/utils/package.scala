package com.tacplatform.state

import com.tacplatform.account.Address
import com.tacplatform.api.common.AddressTransactions
import com.tacplatform.common.state.ByteStr
import com.tacplatform.database.{LevelDBWriter, TestStorageFactory}
import com.tacplatform.events.BlockchainUpdateTriggers
import com.tacplatform.settings.TestSettings._
import com.tacplatform.settings.{BlockchainSettings, FunctionalitySettings, GenesisSettings, RewardsSettings, TestSettings}
import com.tacplatform.transaction.{Asset, Transaction}
import com.tacplatform.utils.SystemTime
import monix.reactive.Observer
import org.iq80.leveldb.DB

package object utils {

  def addressTransactions(
      db: DB,
      diff: => Option[(Height, Diff)],
      address: Address,
      types: Set[Transaction.Type],
      fromId: Option[ByteStr]
  ): Seq[(Height, Transaction)] =
    AddressTransactions.allAddressTransactions(db, diff, address, None, types, fromId).map { case (h, tx, _) => h -> tx }.toSeq

  object TestLevelDB {
    def withFunctionalitySettings(
        writableDB: DB,
        spendableBalanceChanged: Observer[(Address, Asset)],
        fs: FunctionalitySettings
    ): LevelDBWriter =
      TestStorageFactory(
        TestSettings.Default.withFunctionalitySettings(fs),
        writableDB,
        SystemTime,
        spendableBalanceChanged,
        BlockchainUpdateTriggers.noop
      )._2

    def createTestBlockchainSettings(fs: FunctionalitySettings): BlockchainSettings =
      BlockchainSettings('T', fs, GenesisSettings.TESTNET, RewardsSettings.TESTNET)
  }
}
