package com.tacplatform.api.http.requests

import com.tacplatform.account.PublicKey
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.Transaction
import com.tacplatform.transaction.TxValidationError.GenericError

trait TxBroadcastRequest {
  def sender: Option[String]
  def senderPublicKey: Option[String]

  def toTxFrom(sender: PublicKey): Either[ValidationError, Transaction]

  def toTx: Either[ValidationError, Transaction] =
    for {
      sender <- senderPublicKey match {
        case Some(key) => PublicKey.fromBase58String(key)
        case None      => Left(GenericError("invalid.senderPublicKey"))
      }
      tx <- toTxFrom(sender)
    } yield tx
}
