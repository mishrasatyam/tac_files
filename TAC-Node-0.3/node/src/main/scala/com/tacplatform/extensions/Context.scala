package com.tacplatform.extensions

import akka.actor.ActorSystem
import com.tacplatform.account.Address
import com.tacplatform.api.common._
import com.tacplatform.common.state.ByteStr
import com.tacplatform.events.UtxEvent
import com.tacplatform.lang.ValidationError
import com.tacplatform.settings.TacSettings
import com.tacplatform.state.Blockchain
import com.tacplatform.transaction.smart.script.trace.TracedResult
import com.tacplatform.transaction.{Asset, DiscardedBlocks, Transaction}
import com.tacplatform.utils.Time
import com.tacplatform.utx.UtxPool
import com.tacplatform.wallet.Wallet
import monix.eval.Task
import monix.reactive.Observable

trait Context {
  def settings: TacSettings
  def blockchain: Blockchain
  def rollbackTo(blockId: ByteStr): Task[Either[ValidationError, DiscardedBlocks]]
  def time: Time
  def wallet: Wallet
  def utx: UtxPool

  def transactionsApi: CommonTransactionsApi
  def blocksApi: CommonBlocksApi
  def accountsApi: CommonAccountsApi
  def assetsApi: CommonAssetsApi

  def broadcastTransaction(tx: Transaction): TracedResult[ValidationError, Boolean]
  def spendableBalanceChanged: Observable[(Address, Asset)]
  def utxEvents: Observable[UtxEvent]
  def actorSystem: ActorSystem
}
