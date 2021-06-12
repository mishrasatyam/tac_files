package com.tacplatform

import com.tacplatform.block.SignedBlockHeader
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.network.TransactionPublisher
import com.tacplatform.settings.TacSettings
import com.tacplatform.state.diffs.TransactionDiffer
import com.tacplatform.state.{Blockchain, LeaseBalance, NG, VolumeAndFee}
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.{Transaction, TxHelpers}
import org.scalamock.MockFactoryBase

import scala.concurrent.Future

trait BlockchainStubHelpers { self: MockFactoryBase =>
  def createBlockchainStub(f: Blockchain => Unit = _ => ()): Blockchain with NG = {
    trait Blockchain1 extends Blockchain with NG
    val blockchain = stub[Blockchain1]
    f(blockchain) // Overrides
    (() => blockchain.settings).when().returns(TacSettings.default().blockchainSettings)
    (() => blockchain.activatedFeatures)
      .when()
      .returns(
        Map(
          BlockchainFeatures.BlockV5.id             -> 0,
          BlockchainFeatures.SmartAccounts.id       -> 0,
          BlockchainFeatures.SmartAssets.id         -> 0,
          BlockchainFeatures.SmartAccountTrading.id -> 0,
          BlockchainFeatures.OrderV3.id             -> 0,
          BlockchainFeatures.Ride4DApps.id          -> 0
        )
      )
    (blockchain.accountScript _).when(*).returns(None)
    (blockchain.leaseBalance _).when(*).returns(LeaseBalance.empty)
    (() => blockchain.height).when().returns(1)
    (blockchain.blockHeader _).when(*).returns {
      val block = TestBlock.create(Nil)
      Some(SignedBlockHeader(block.header, block.signature))
    }
    (blockchain.filledVolumeAndFee _).when(*).returns(VolumeAndFee.empty)
    (blockchain.assetDescription _).when(*).returns(None)
    (blockchain.balance _).when(TxHelpers.defaultAddress, Tac).returns(Long.MaxValue / 3)
    blockchain
  }

  def createTxPublisherStub(blockchain: Blockchain): TransactionPublisher = {
    val publisher = stub[TransactionPublisher]
    (publisher.validateAndBroadcast _).when(*, *).onCall {
      case (transaction: Transaction, _) =>
        val differ = TransactionDiffer(blockchain.lastBlockTimestamp, System.currentTimeMillis())(blockchain, _)
        val result = differ(transaction)
        Future.successful(result.map(_ => true))
    }
    publisher
  }
}
