package com.tacplatform.transaction.serialization.impl

import cats.implicits._
import com.tacplatform.protobuf.transaction.{PBTransactions, SignedTransaction => PBSignedTransaction}
import com.tacplatform.protobuf.utils.PBUtils
import com.tacplatform.transaction.{PBParsingError, Transaction}

import scala.util.Try

object PBTransactionSerializer {
  def bodyBytes(tx: Transaction): Array[Byte] =
    PBUtils.encodeDeterministic(PBTransactions.protobuf(tx).getTransaction)

  def bytes(tx: Transaction): Array[Byte] =
    PBUtils.encodeDeterministic(PBTransactions.protobuf(tx))

  def parseBytes(bytes: Array[Byte]): Try[Transaction] =
    PBSignedTransaction
      .validate(bytes)
      .adaptErr { case err => PBParsingError(err) }
      .flatMap(PBTransactions.tryToVanilla)
}
