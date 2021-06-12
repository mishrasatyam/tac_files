package com.tacplatform.transaction.assets

import com.tacplatform.account.{AddressScheme, KeyPair, PrivateKey, PublicKey}
import com.tacplatform.crypto
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction._
import com.tacplatform.transaction.serialization.impl.ReissueTxSerializer
import com.tacplatform.transaction.validation.TxValidator
import com.tacplatform.transaction.validation.impl.ReissueTxValidator
import monix.eval.Coeval
import play.api.libs.json.JsObject

import scala.util._

case class ReissueTransaction(
    version: TxVersion,
    sender: PublicKey,
    asset: IssuedAsset,
    quantity: TxAmount,
    reissuable: Boolean,
    fee: TxAmount,
    timestamp: TxTimestamp,
    proofs: Proofs,
    chainId: Byte
) extends VersionedTransaction
    with ProvenTransaction
    with SigProofsSwitch
    with TxWithFee.InTac
    with FastHashId
    with LegacyPBSwitch.V3 {

  //noinspection TypeAnnotation
  override val builder = ReissueTransaction

  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(builder.serializer.bodyBytes(this))
  override val bytes: Coeval[Array[Byte]]     = Coeval.evalOnce(builder.serializer.toBytes(this))
  override val json: Coeval[JsObject]         = Coeval.evalOnce(builder.serializer.toJson(this))

  override def checkedAssets: Seq[IssuedAsset] = Seq(asset)
}

object ReissueTransaction extends TransactionParser {
  type TransactionT = ReissueTransaction

  override val typeId: TxType                    = 5: Byte
  override def supportedVersions: Set[TxVersion] = Set(1, 2, 3)

  implicit val validator: TxValidator[ReissueTransaction] = ReissueTxValidator
  implicit def sign(tx: ReissueTransaction, privateKey: PrivateKey): ReissueTransaction =
    tx.copy(proofs = Proofs(crypto.sign(privateKey, tx.bodyBytes())))

  val serializer = ReissueTxSerializer

  override def parseBytes(bytes: Array[TxVersion]): Try[ReissueTransaction] =
    serializer.parseBytes(bytes)

  def create(
      version: TxVersion,
      sender: PublicKey,
      asset: IssuedAsset,
      quantity: Long,
      reissuable: Boolean,
      fee: Long,
      timestamp: Long,
      proofs: Proofs,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, ReissueTransaction] =
    ReissueTransaction(version, sender, asset, quantity, reissuable, fee, timestamp, proofs, chainId).validatedEither

  def signed(
      version: TxVersion,
      sender: PublicKey,
      asset: IssuedAsset,
      quantity: Long,
      reissuable: Boolean,
      fee: Long,
      timestamp: Long,
      signer: PrivateKey
  ): Either[ValidationError, ReissueTransaction] =
    create(version, sender, asset, quantity, reissuable, fee, timestamp, Nil).map(_.signWith(signer))

  def selfSigned(
      version: TxVersion,
      sender: KeyPair,
      asset: IssuedAsset,
      quantity: Long,
      reissuable: Boolean,
      fee: Long,
      timestamp: Long
  ): Either[ValidationError, ReissueTransaction] =
    signed(version, sender.publicKey, asset, quantity, reissuable, fee, timestamp, sender.privateKey)
}
