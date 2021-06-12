package com.tacplatform.state

import com.tacplatform.block.Block
import com.tacplatform.common.state.ByteStr
import com.tacplatform.crypto._
import com.tacplatform.lagonaki.mocks.TestBlock

trait HistoryTest {
  val genesisBlock: Block = TestBlock.withReference(ByteStr(Array.fill(SignatureLength)(0: Byte)))

  def getNextTestBlock(blockchain: Blockchain): Block =
    TestBlock.withReference(blockchain.lastBlockId.get)

  def getNextTestBlockWithVotes(blockchain: Blockchain, votes: Seq[Short]): Block =
    TestBlock.withReferenceAndFeatures(blockchain.lastBlockId.get, votes)
}
