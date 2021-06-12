package com.tacplatform.api.http.requests

import cats.implicits._
import com.tacplatform.account.PublicKey
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.assets.UpdateAssetInfoTransaction
import com.tacplatform.transaction.{AssetIdStringLength, Proofs, Transaction, TxAmount, TxTimestamp, TxVersion}
import play.api.libs.json.Json

case class UpdateAssetInfoRequest(
    version: TxVersion,
    chainId: Byte,
    sender: Option[String],
    senderPublicKey: Option[String],
    assetId: String,
    name: String,
    description: String,
    timestamp: Option[TxTimestamp],
    fee: TxAmount,
    feeAssetId: Option[String],
    proofs: Option[Proofs]
) extends TxBroadcastRequest {
  override def toTxFrom(sender: PublicKey): Either[ValidationError, Transaction] =
    for {
      _assetId <- parseBase58(assetId, "invalid.assetId", AssetIdStringLength)
      _feeAssetId <- feeAssetId
        .traverse(parseBase58(_, "invalid.assetId", AssetIdStringLength).map(IssuedAsset))
        .map(_ getOrElse Tac)
      tx <- UpdateAssetInfoTransaction
        .create(version, sender, _assetId, name, description, timestamp.getOrElse(0L), fee, _feeAssetId, proofs.getOrElse(Proofs.empty), chainId)
    } yield tx
}

object UpdateAssetInfoRequest {
  implicit val jsonFormat = Json.format[UpdateAssetInfoRequest]
}
