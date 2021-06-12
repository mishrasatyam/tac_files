package com.tacplatform.state.appender

import cats.data.EitherT
import com.tacplatform.block.Block.BlockId
import com.tacplatform.block.MicroBlock
import com.tacplatform.lang.ValidationError
import com.tacplatform.metrics.{BlockStats, _}
import com.tacplatform.network.MicroBlockSynchronizer.MicroblockData
import com.tacplatform.network._
import com.tacplatform.state.Blockchain
import com.tacplatform.transaction.BlockchainUpdater
import com.tacplatform.transaction.TxValidationError.InvalidSignature
import com.tacplatform.utils.ScorexLogging
import com.tacplatform.utx.UtxPool
import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import kamon.Kamon
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Left, Right}

object MicroblockAppender extends ScorexLogging {
  def apply(blockchainUpdater: BlockchainUpdater with Blockchain, utxStorage: UtxPool, scheduler: Scheduler, verify: Boolean = true)(
      microBlock: MicroBlock
  ): Task[Either[ValidationError, BlockId]] = {

    Task(metrics.microblockProcessingTimeStats.measureSuccessful {
      blockchainUpdater
        .processMicroBlock(microBlock, verify)
        .map { totalBlockId =>
          if (microBlock.transactionData.nonEmpty) log.trace {
            s"Removing mined txs from ${microBlock.stringRepr(totalBlockId)}: ${microBlock.transactionData.map(_.id()).mkString(", ")}"
          }
          utxStorage.removeAll(microBlock.transactionData)
          totalBlockId
        }
    }).executeOn(scheduler)
  }

  def apply(
      blockchainUpdater: BlockchainUpdater with Blockchain,
      utxStorage: UtxPool,
      allChannels: ChannelGroup,
      peerDatabase: PeerDatabase,
      scheduler: Scheduler
  )(ch: Channel, md: MicroblockData): Task[Unit] = {
    import md.microBlock
    val microblockTotalResBlockSig = microBlock.totalResBlockSig
    (for {
      _ <- EitherT(Task.now(microBlock.signaturesValid()))
      _ <- EitherT(apply(blockchainUpdater, utxStorage, scheduler)(microBlock))
    } yield ()).value.map {
      case Right(_) =>
        md.invOpt match {
          case Some(mi) => allChannels.broadcast(mi, except = md.microblockOwners())
          case None     => log.warn(s"${id(ch)} Not broadcasting MicroBlockInv")
        }
        BlockStats.applied(microBlock)
      case Left(is: InvalidSignature) =>
        peerDatabase.blacklistAndClose(ch, s"Could not append microblock $microblockTotalResBlockSig: $is")
      case Left(ve) =>
        BlockStats.declined(microBlock)
        log.debug(s"${id(ch)} Could not append microblock $microblockTotalResBlockSig: $ve")
    }
  }

  private[this] object metrics {
    val microblockProcessingTimeStats = Kamon.timer("microblock-appender.processing-time").withoutTags()
  }
}
