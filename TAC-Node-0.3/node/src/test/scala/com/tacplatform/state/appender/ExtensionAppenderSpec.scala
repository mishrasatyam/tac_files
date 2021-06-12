package com.tacplatform.state.appender

import com.tacplatform.block.Block
import com.tacplatform.common.utils._
import com.tacplatform.db.WithDomain
import com.tacplatform.network.{ExtensionBlocks, InvalidBlockStorage, PeerDatabase}
import com.tacplatform.transaction.TxHelpers
import com.tacplatform.utils.SystemTime
import com.tacplatform.utx.UtxPoolImpl
import com.tacplatform.TestTime
import monix.execution.Scheduler.Implicits.global
import monix.reactive.subjects.ConcurrentSubject
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.{FlatSpec, Matchers}

class ExtensionAppenderSpec extends FlatSpec with Matchers with WithDomain with PathMockFactory {
  "Extension appender" should "drop duplicate transactions from UTX" in withDomain() { d =>
    val utx = new UtxPoolImpl(SystemTime, d.blockchain, ConcurrentSubject.publish, SettingsFromDefaultConfig.utxSettings)
    val time = new TestTime()
    val extensionAppender = ExtensionAppender(d.blockchain, utx, d.posSelector, time, stub[InvalidBlockStorage], stub[PeerDatabase], global)(null, _)

    d.appendBlock(TxHelpers.genesis(TxHelpers.defaultAddress))
    val tx = TxHelpers.transfer()
    val block1 = d.createBlock(Block.PlainBlockVersion, Seq(tx), strictTime = true)
    utx.putIfNew(tx).resultE.explicitGet()
    d.appendBlock(tx)
    utx.all shouldBe Seq(tx)

    time.setTime(block1.header.timestamp)
    extensionAppender(ExtensionBlocks(d.blockchain.score + block1.blockScore(), Seq(block1))).runSyncUnsafe().explicitGet()
    d.blockchain.height shouldBe 2
    utx.all shouldBe Nil
  }

}
