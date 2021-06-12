package com.tacplatform.api.http.requests

import com.tacplatform.account.PublicKey
import com.tacplatform.lang.ValidationError
import com.tacplatform.lang.script.Script
import com.tacplatform.transaction.Proofs
import com.tacplatform.transaction.smart.SetScriptTransaction
import play.api.libs.functional.syntax._
import play.api.libs.json._

object SignedSetScriptRequest {
  implicit val signedSetScriptRequestReads: Reads[SignedSetScriptRequest] = (
    (JsPath \ "version").readNullable[Byte] and
      (JsPath \ "senderPublicKey").read[String] and
      (JsPath \ "script").readNullable[String] and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "proofs").read[Proofs]
  )(SignedSetScriptRequest.apply _)

  implicit val signedSetScriptRequestWrites: OWrites[SignedSetScriptRequest] =
    Json.writes[SignedSetScriptRequest].transform((request: JsObject) => request + ("version" -> JsNumber(1)))
}

case class SignedSetScriptRequest(
    version: Option[Byte],
    senderPublicKey: String,
    script: Option[String],
    fee: Long,
    timestamp: Long,
    proofs: Proofs
) {
  def toTx: Either[ValidationError, SetScriptTransaction] =
    for {
      _sender <- PublicKey.fromBase58String(senderPublicKey)
      _script <- script match {
        case None | Some("") => Right(None)
        case Some(s)         => Script.fromBase64String(s).map(Some(_))
      }
      t <- SetScriptTransaction.create(version.getOrElse(1.toByte), _sender, _script, fee, timestamp, proofs)
    } yield t
}
