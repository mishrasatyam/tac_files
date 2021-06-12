package com.tacplatform.consensus.nxt

import com.tacplatform.account.{Address, KeyPair}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.consensus.TransactionsOrdering
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.transfer._
import org.scalatest.{Assertions, Matchers, PropSpec}

import scala.util.Random

class TransactionsOrderingSpecification extends PropSpec with Assertions with Matchers {

  private val kp: KeyPair = KeyPair(ByteStr(new Array[Byte](32)))
  property("TransactionsOrdering.InBlock should sort correctly") {
    val correctSeq = Seq(
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          125L,
          ByteStr.empty,
          1
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          124L,
          ByteStr.empty,
          2
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          124L,
          ByteStr.empty,
          1
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Asset.fromCompatId(Some(ByteStr.empty)),
          124L,
          ByteStr.empty,
          2
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Asset.fromCompatId(Some(ByteStr.empty)),
          124L,
          ByteStr.empty,
          1
        )
        .explicitGet()
    )

    val sorted = Random.shuffle(correctSeq).sorted(TransactionsOrdering.InBlock)

    sorted shouldBe correctSeq
  }

  property("TransactionsOrdering.InUTXPool should sort correctly") {
    val correctSeq = Seq(
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          124L,
          ByteStr.empty,
          1
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          123L,
          ByteStr.empty,
          1
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          123L,
          ByteStr.empty,
          2
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Asset.fromCompatId(Some(ByteStr.empty)),
          124L,
          ByteStr.empty,
          1
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Asset.fromCompatId(Some(ByteStr.empty)),
          124L,
          ByteStr.empty,
          2
        )
        .explicitGet()
    )

    val sorted = Random.shuffle(correctSeq).sorted(TransactionsOrdering.InUTXPool(Set.empty))

    sorted shouldBe correctSeq
  }

  property("TransactionsOrdering.InBlock should sort txs by decreasing block timestamp") {
    val correctSeq = Seq(
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          1,
          ByteStr.empty,
          124L
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          kp,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          1,
          ByteStr.empty,
          123L
        )
        .explicitGet()
    )

    Random.shuffle(correctSeq).sorted(TransactionsOrdering.InBlock) shouldBe correctSeq
  }

  property("TransactionsOrdering.InUTXPool should sort txs by ascending block timestamp taking into consideration whitelisted senders") {
    val whitelisted = KeyPair(Array.fill(32)(1: Byte))
    val correctSeq = Seq(
      TransferTransaction
        .selfSigned(
          1.toByte,
          whitelisted,
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          2,
          ByteStr.empty,
          123L
        )
        .explicitGet(),
      TransferTransaction
        .selfSigned(
          1.toByte,
          KeyPair(Array.fill(32)(0: Byte)),
          Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU").explicitGet(),
          Tac,
          100000,
          Tac,
          2,
          ByteStr.empty,
          124L
        )
        .explicitGet()
    )
    Random.shuffle(correctSeq).sorted(TransactionsOrdering.InUTXPool(Set(whitelisted.toAddress.stringRepr))) shouldBe correctSeq
  }
}
