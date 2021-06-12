package com.tacplatform.utils

import com.typesafe.config.ConfigFactory
import com.tacplatform.account.{Address, Alias}
import com.tacplatform.block.SignedBlockHeader
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.settings.BlockchainSettings
import com.tacplatform.state._
import com.tacplatform.state.reader.LeaseDetails
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{Asset, Transaction}

trait EmptyBlockchain extends Blockchain {
  override lazy val settings: BlockchainSettings = BlockchainSettings.fromRootConfig(ConfigFactory.load())

  override def height: Int = 0

  override def score: BigInt = 0

  override def blockHeader(height: Int): Option[SignedBlockHeader] = None

  override def hitSource(height: Int): Option[ByteStr] = None

  override def carryFee: Long = 0

  override def heightOf(blockId: ByteStr): Option[Int] = None

  /** Features related */
  override def approvedFeatures: Map[Short, Int] = Map.empty

  override def activatedFeatures: Map[Short, Int] = Map.empty

  override def featureVotes(height: Int): Map[Short, Int] = Map.empty

  /** Block reward related */
  override def blockReward(height: Int): Option[Long] = None

  override def blockRewardVotes(height: Int): Seq[Long] = Seq.empty

  override def tacAmount(height: Int): BigInt = 0

  override def transferById(id: ByteStr): Option[(Int, TransferTransaction)] = None

  override def transactionInfo(id: ByteStr): Option[(Int, Transaction, Boolean)] = None

  override def transactionMeta(id: ByteStr): Option[(Int, Boolean)] = None

  override def containsTransaction(tx: Transaction): Boolean = false

  override def assetDescription(id: IssuedAsset): Option[AssetDescription] = None

  override def resolveAlias(a: Alias): Either[ValidationError, Address] = Left(GenericError("Empty blockchain"))

  override def leaseDetails(leaseId: ByteStr): Option[LeaseDetails] = None

  override def filledVolumeAndFee(orderId: ByteStr): VolumeAndFee = VolumeAndFee(0, 0)

  /** Retrieves Tac balance snapshot in the [from, to] range (inclusive) */
  override def balanceAtHeight(address: Address, height: Int, assetId: Asset = Tac): Option[(Int, Long)] = Option.empty
  override def balanceSnapshots(address: Address, from: Int, to: Option[ByteStr]): Seq[BalanceSnapshot]         = Seq.empty

  override def accountScript(address: Address): Option[AccountScriptInfo] = None

  override def hasAccountScript(address: Address): Boolean = false

  override def assetScript(asset: IssuedAsset): Option[AssetScriptInfo] = None

  override def accountData(acc: Address, key: String): Option[DataEntry[_]] = None

  override def hasData(acc: Address): Boolean = false

  override def balance(address: Address, mayBeAssetId: Asset): Long = 0

  override def leaseBalance(address: Address): LeaseBalance = LeaseBalance.empty
}

object EmptyBlockchain extends EmptyBlockchain
