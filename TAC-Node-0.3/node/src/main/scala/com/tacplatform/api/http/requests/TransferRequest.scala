package com.tacplatform.api.http.requests

import com.tacplatform.account.{AddressOrAlias, PublicKey}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{Asset, Proofs}
import play.api.libs.json._

case class TransferRequest(
    version: Option[Byte],
    sender: Option[String],
    senderPublicKey: Option[String],
    recipient: String,
    assetId: Option[Asset],
    amount: Long,
    feeAssetId: Option[Asset],
    fee: Long,
    attachment: Option[ByteStr] = None,
    timestamp: Option[Long]= None,
    signature: Option[ByteStr] = None,
    proofs: Option[Proofs] = None
) extends TxBroadcastRequest {
  def toTxFrom(sender: PublicKey): Either[ValidationError, TransferTransaction] =
    for {
      validRecipient <- AddressOrAlias.fromString(recipient)
      validProofs    <- toProofs(signature, proofs)
      tx <- TransferTransaction.create(
        version.getOrElse(1.toByte),
        sender,
        validRecipient,
        assetId.getOrElse(Asset.Tac),
        amount,
        feeAssetId.getOrElse(Asset.Tac),
        fee,
        attachment.getOrElse(ByteStr.empty),
        timestamp.getOrElse(0L),
        validProofs
      )
    } yield tx
}

object TransferRequest {
  implicit val jsonFormat: Format[TransferRequest] = Json.format
}
