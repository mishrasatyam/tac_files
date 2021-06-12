package com.tacplatform

import com.tacplatform.account.{Address, AddressOrAlias, KeyPair}
import com.tacplatform.block.{Block, MicroBlock}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils._
import com.tacplatform.history.DefaultBaseTarget
import com.tacplatform.lang.script.Script
import com.tacplatform.lang.v1.compiler.Terms.FUNCTION_CALL
import com.tacplatform.protobuf.block.PBBlocks
import com.tacplatform.state.StringDataEntry
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.assets.IssueTransaction
import com.tacplatform.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.tacplatform.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{DataTransaction, Transaction, TxVersion}
import com.tacplatform.utils._
import org.scalacheck.Gen

trait BlocksTransactionsHelpers { self: TransactionGen =>
  object QuickTX {
    val FeeAmount = 400000

    def transfer(
        from: KeyPair,
        to: AddressOrAlias = accountGen.sample.get.toAddress,
        amount: Long = smallFeeGen.sample.get,
        timestamp: Gen[Long] = timestampGen
    ): Gen[Transaction] =
      for {
        timestamp <- timestamp
      } yield TransferTransaction.selfSigned(1.toByte, from, to, Tac, amount, Tac, FeeAmount, ByteStr.empty, timestamp).explicitGet()

    def transferV2(
        from: KeyPair,
        to: AddressOrAlias = accountGen.sample.get.toAddress,
        amount: Long = smallFeeGen.sample.get,
        timestamp: Gen[Long] = timestampGen
    ): Gen[Transaction] =
      for {
        timestamp <- timestamp
      } yield TransferTransaction.selfSigned(2.toByte, from, to, Tac, amount, Tac, FeeAmount, ByteStr.empty, timestamp).explicitGet()

    def transferAsset(
        asset: IssuedAsset,
        from: KeyPair,
        to: AddressOrAlias = accountGen.sample.get.toAddress,
        amount: Long = smallFeeGen.sample.get,
        timestamp: Gen[Long] = timestampGen
    ): Gen[Transaction] =
      for {
        timestamp <- timestamp
      } yield TransferTransaction.selfSigned(1.toByte, from, to, asset, amount, Tac, FeeAmount, ByteStr.empty, timestamp).explicitGet()

    def lease(
        from: KeyPair,
        to: AddressOrAlias = accountGen.sample.get.toAddress,
        amount: Long = smallFeeGen.sample.get,
        timestamp: Gen[Long] = timestampGen
    ): Gen[LeaseTransaction] =
      for {
        timestamp <- timestamp
      } yield LeaseTransaction.selfSigned(1.toByte, from, to, amount, FeeAmount, timestamp).explicitGet()

    def leaseCancel(from: KeyPair, leaseId: ByteStr, timestamp: Gen[Long] = timestampGen): Gen[LeaseCancelTransaction] =
      for {
        timestamp <- timestamp
      } yield LeaseCancelTransaction.selfSigned(1.toByte, from, leaseId, FeeAmount, timestamp).explicitGet()

    def data(from: KeyPair, dataKey: String, timestamp: Gen[Long] = timestampGen): Gen[DataTransaction] =
      for {
        timestamp <- timestamp
      } yield DataTransaction.selfSigned(1.toByte, from, List(StringDataEntry(dataKey, Gen.numStr.sample.get)), FeeAmount, timestamp).explicitGet()

    def nftIssue(from: KeyPair, timestamp: Gen[Long] = timestampGen): Gen[IssueTransaction] =
      for {
        timestamp <- timestamp
      } yield IssueTransaction(
        TxVersion.V1,
        from.publicKey,
        "test".utf8Bytes,
        Array.emptyByteArray,
        1,
        0,
        reissuable = false,
        script = None,
        100000000L,
        timestamp
      ).signWith(from.privateKey)

    def setScript(from: KeyPair, script: Script, timestamp: Gen[Long] = timestampGen): Gen[SetScriptTransaction] =
      for {
        timestamp <- timestamp
      } yield SetScriptTransaction.selfSigned(1.toByte, from, Some(script), FeeAmount, timestamp).explicitGet()

    def invokeScript(
        from: KeyPair,
        dapp: Address,
        call: FUNCTION_CALL,
        payments: Seq[InvokeScriptTransaction.Payment] = Nil,
        timestamp: Gen[Long] = timestampGen
    ): Gen[InvokeScriptTransaction] =
      for {
        timestamp <- timestamp
      } yield InvokeScriptTransaction.selfSigned(1.toByte, from, dapp, Some(call), payments, FeeAmount * 2, Tac, timestamp).explicitGet()
  }

  object UnsafeBlocks {
    def unsafeChainBaseAndMicro(
        totalRefTo: ByteStr,
        base: Seq[Transaction],
        micros: Seq[Seq[Transaction]],
        signer: KeyPair,
        version: Byte,
        timestamp: Long
    ): (Block, Seq[MicroBlock]) = {
      val block = unsafeBlock(totalRefTo, base, signer, version, timestamp)
      val microBlocks = micros
        .foldLeft((block, Seq.empty[MicroBlock])) {
          case ((lastTotal, allMicros), txs) =>
            val (newTotal, micro) = unsafeMicro(totalRefTo, lastTotal, txs, signer, version, timestamp)
            (newTotal, allMicros :+ micro)
        }
        ._2
      (block, microBlocks)
    }

    def unsafeMicro(
        totalRefTo: ByteStr,
        prevTotal: Block,
        txs: Seq[Transaction],
        signer: KeyPair,
        version: TxVersion,
        ts: Long
    ): (Block, MicroBlock) = {
      val newTotalBlock = unsafeBlock(totalRefTo, prevTotal.transactionData ++ txs, signer, version, ts)
      val unsigned      = new MicroBlock(version, signer.publicKey, txs, prevTotal.id(), newTotalBlock.signature, ByteStr.empty)
      val signature     = crypto.sign(signer.privateKey, unsigned.bytes())
      val signed        = unsigned.copy(signature = signature)
      (newTotalBlock, signed)
    }

    def unsafeBlock(
        reference: ByteStr,
        txs: Seq[Transaction],
        signer: KeyPair,
        version: Byte,
        timestamp: Long,
        bTarget: Long = DefaultBaseTarget
    ): Block = {
      val unsigned: Block = Block.create(
        version = version,
        timestamp = timestamp,
        reference = reference,
        baseTarget = bTarget,
        generationSignature = com.tacplatform.history.generationSignature,
        generator = signer.publicKey,
        featureVotes = Seq.empty,
        rewardVote = -1L,
        transactionData = txs
      )
      val toSign =
        if (version < Block.ProtoBlockVersion) unsigned.bytes()
        else PBBlocks.protobuf(unsigned).header.get.toByteArray
      unsigned.copy(signature = crypto.sign(signer.privateKey, toSign))
    }
  }
}
