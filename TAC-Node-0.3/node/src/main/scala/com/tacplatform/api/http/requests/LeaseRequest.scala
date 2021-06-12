package com.tacplatform.api.http.requests

import com.tacplatform.account.{AddressOrAlias, PublicKey}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.Proofs
import com.tacplatform.transaction.lease.LeaseTransaction
import play.api.libs.json.{Format, Json}

case class LeaseRequest(
    version: Option[Byte],
    sender: Option[String],
    senderPublicKey: Option[String],
    recipient: String,
    amount: Long,
    fee: Long,
    timestamp: Option[Long],
    signature: Option[ByteStr],
    proofs: Option[Proofs]
) extends TxBroadcastRequest {
  def toTxFrom(sender: PublicKey): Either[ValidationError, LeaseTransaction] =
    for {
      validRecipient <- AddressOrAlias.fromString(recipient)
      validProofs    <- toProofs(signature, proofs)
      tx <- LeaseTransaction.create(
        version.getOrElse(1.toByte),
        sender,
        validRecipient,
        amount,
        fee,
        timestamp.getOrElse(0L),
        validProofs
      )
    } yield tx
}

object LeaseRequest {
  implicit val jsonFormat: Format[LeaseRequest] = Json.format
}
