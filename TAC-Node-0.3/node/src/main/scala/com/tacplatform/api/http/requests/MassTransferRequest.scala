package com.tacplatform.api.http.requests

import com.tacplatform.common.state.ByteStr
import com.tacplatform.transaction.transfer.MassTransferTransaction.Transfer
import play.api.libs.json.Json

case class MassTransferRequest(
    version: Option[Byte],
    assetId: Option[String],
    sender: String,
    transfers: List[Transfer],
    fee: Long,
    attachment: Option[ByteStr] = None,
    timestamp: Option[Long] = None
)

object MassTransferRequest {
  implicit val jsonFormat = Json.format[MassTransferRequest]
}
