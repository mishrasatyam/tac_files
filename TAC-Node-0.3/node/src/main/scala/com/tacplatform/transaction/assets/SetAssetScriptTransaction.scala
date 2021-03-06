package com.tacplatform.transaction.assets

import com.tacplatform.account._
import com.tacplatform.crypto
import com.tacplatform.lang.ValidationError
import com.tacplatform.lang.script.Script
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction._
import com.tacplatform.transaction.serialization.impl.SetAssetScriptTxSerializer
import com.tacplatform.transaction.validation.TxValidator
import com.tacplatform.transaction.validation.impl.SetAssetScriptTxValidator
import monix.eval.Coeval
import play.api.libs.json.JsObject

import scala.util.Try

case class SetAssetScriptTransaction(
    version: TxVersion,
    sender: PublicKey,
    asset: IssuedAsset,
    script: Option[Script],
    fee: TxAmount,
    timestamp: TxTimestamp,
    proofs: Proofs,
    chainId: Byte
) extends VersionedTransaction
    with ProvenTransaction
    with TxWithFee.InTac
    with FastHashId
    with LegacyPBSwitch.V2 {

  //noinspection TypeAnnotation
  override val builder = SetAssetScriptTransaction

  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(builder.serializer.bodyBytes(this))
  override val bytes: Coeval[Array[Byte]]     = Coeval.evalOnce(builder.serializer.toBytes(this))
  override val json: Coeval[JsObject]         = Coeval.evalOnce(builder.serializer.toJson(this))

  override val checkedAssets: Seq[IssuedAsset] = Seq(asset)
}

object SetAssetScriptTransaction extends TransactionParser {
  type TransactionT = SetAssetScriptTransaction

  override val typeId: TxType                    = 15: Byte
  override val supportedVersions: Set[TxVersion] = Set(1, 2)

  implicit val validator: TxValidator[SetAssetScriptTransaction] = SetAssetScriptTxValidator

  implicit def sign(tx: SetAssetScriptTransaction, privateKey: PrivateKey): SetAssetScriptTransaction =
    tx.copy(proofs = Proofs(crypto.sign(privateKey, tx.bodyBytes())))

  val serializer = SetAssetScriptTxSerializer

  override def parseBytes(bytes: Array[TxVersion]): Try[SetAssetScriptTransaction] =
    serializer.parseBytes(bytes)

  def create(
      version: TxVersion,
      sender: PublicKey,
      assetId: IssuedAsset,
      script: Option[Script],
      fee: TxAmount,
      timestamp: TxTimestamp,
      proofs: Proofs,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, SetAssetScriptTransaction] =
    SetAssetScriptTransaction(version, sender, assetId, script, fee, timestamp, proofs, chainId).validatedEither

  def signed(
      version: TxVersion,
      sender: PublicKey,
      asset: IssuedAsset,
      script: Option[Script],
      fee: TxAmount,
      timestamp: TxTimestamp,
      signer: PrivateKey
  ): Either[ValidationError, SetAssetScriptTransaction] =
    create(version, sender, asset, script, fee, timestamp, Proofs.empty).map(_.signWith(signer))

  def selfSigned(
      version: TxVersion,
      sender: KeyPair,
      asset: IssuedAsset,
      script: Option[Script],
      fee: TxAmount,
      timestamp: TxTimestamp
  ): Either[ValidationError, SetAssetScriptTransaction] =
    signed(version, sender.publicKey, asset, script, fee, timestamp, sender.privateKey)
}
