package com.tacplatform.api.http.requests

import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.Proofs
import com.tacplatform.transaction.assets.exchange._
import play.api.libs.json.{Format, Json}

object SignedExchangeRequest {
  implicit val orderFormat: Format[Order]                                 = com.tacplatform.transaction.assets.exchange.OrderJson.orderFormat
  implicit val signedExchangeRequestFormat: Format[SignedExchangeRequest] = Json.format
}

case class SignedExchangeRequest(
    senderPublicKey: String,
    order1: Order,
    order2: Order,
    amount: Long,
    price: Long,
    fee: Long,
    buyMatcherFee: Long,
    sellMatcherFee: Long,
    timestamp: Long,
    signature: String
) {
  def toTx: Either[ValidationError, ExchangeTransaction] =
    for {
      _signature <- parseBase58(signature, "invalid.signature", SignatureStringLength)
      _t         <- ExchangeTransaction.create(1.toByte, order1, order2, amount, price, buyMatcherFee, sellMatcherFee, fee, timestamp, Proofs(_signature))
    } yield _t

}
