package com.tacplatform.api.http.requests

import com.tacplatform.account.PublicKey
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.Proofs
import com.tacplatform.transaction.assets.ReissueTransaction
import play.api.libs.json.{Format, Json}

object SignedReissueV1Request {
  implicit val assetReissueRequestReads: Format[SignedReissueV1Request] = Json.format
}

case class SignedReissueV1Request(
    senderPublicKey: String,
    assetId: String,
    quantity: Long,
    reissuable: Boolean,
    fee: Long,
    timestamp: Long,
    signature: String
) {
  def toTx: Either[ValidationError, ReissueTransaction] =
    for {
      _sender    <- PublicKey.fromBase58String(senderPublicKey)
      _signature <- parseBase58(signature, "invalid.signature", SignatureStringLength)
      _assetId   <- parseBase58ToIssuedAsset(assetId)
      _t         <- ReissueTransaction.create(1.toByte, _sender, _assetId, quantity, reissuable, fee, timestamp, Proofs(_signature))
    } yield _t
}
