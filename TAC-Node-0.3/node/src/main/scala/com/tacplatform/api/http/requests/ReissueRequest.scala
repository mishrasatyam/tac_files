package com.tacplatform.api.http.requests

import com.tacplatform.account.PublicKey
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction.Proofs
import com.tacplatform.transaction.assets.ReissueTransaction
import play.api.libs.json.{Format, Json}

case class ReissueRequest(
    version: Option[Byte],
    sender: Option[String],
    senderPublicKey: Option[String],
    assetId: IssuedAsset,
    quantity: Long,
    reissuable: Boolean,
    fee: Long,
    timestamp: Option[Long],
    signature: Option[ByteStr],
    proofs: Option[Proofs]
) extends TxBroadcastRequest {
  def toTxFrom(sender: PublicKey): Either[ValidationError, ReissueTransaction] =
    for {
      validProofs <- toProofs(signature, proofs)
      tx <- ReissueTransaction.create(
        version.getOrElse(defaultVersion),
        sender,
        assetId,
        quantity,
        reissuable,
        fee,
        timestamp.getOrElse(defaultTimestamp),
        validProofs
      )
    } yield tx
}

object ReissueRequest {
  implicit val jsonFormat: Format[ReissueRequest] = Json.format
}
