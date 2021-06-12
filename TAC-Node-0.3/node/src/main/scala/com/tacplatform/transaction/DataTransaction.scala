package com.tacplatform.transaction

import com.tacplatform.account.{AddressScheme, KeyPair, PrivateKey, PublicKey}
import com.tacplatform.crypto
import com.tacplatform.lang.ValidationError
import com.tacplatform.protobuf.transaction.PBTransactions
import com.tacplatform.state._
import com.tacplatform.transaction.serialization.impl.DataTxSerializer
import com.tacplatform.transaction.validation.TxValidator
import com.tacplatform.transaction.validation.impl.DataTxValidator
import monix.eval.Coeval
import play.api.libs.json._

import scala.util.Try

case class DataTransaction(
    version: TxVersion,
    sender: PublicKey,
    data: Seq[DataEntry[_]],
    fee: TxAmount,
    timestamp: TxTimestamp,
    proofs: Proofs,
    chainId: Byte
) extends ProvenTransaction
    with VersionedTransaction
    with TxWithFee.InTac
    with FastHashId
    with LegacyPBSwitch.V2 {

  //noinspection TypeAnnotation
  override val builder = DataTransaction

  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(builder.serializer.bodyBytes(this))
  override val bytes: Coeval[Array[Byte]]     = Coeval.evalOnce(builder.serializer.toBytes(this))
  override val json: Coeval[JsObject]         = Coeval.eval(builder.serializer.toJson(this))

  private[tacplatform] lazy val protoDataPayload = PBTransactions.protobuf(this).getTransaction.getDataTransaction.toByteArray
}

object DataTransaction extends TransactionParser {
  type TransactionT = DataTransaction

  val MaxBytes: Int      = 150 * 1024 // uses for RIDE CONST_STRING and CONST_BYTESTR
  val MaxProtoBytes: Int = 165890     // uses for RIDE CONST_BYTESTR
  val MaxEntryCount: Int = 100

  override val typeId: TxType                    = 12: Byte
  override val supportedVersions: Set[TxVersion] = Set(1, 2)

  implicit val validator: TxValidator[DataTransaction] = DataTxValidator

  implicit def sign(tx: DataTransaction, privateKey: PrivateKey): DataTransaction =
    tx.copy(proofs = Proofs(crypto.sign(privateKey, tx.bodyBytes())))

  val serializer = DataTxSerializer

  override def parseBytes(bytes: Array[TxVersion]): Try[DataTransaction] =
    serializer.parseBytes(bytes)

  def create(
      version: TxVersion,
      sender: PublicKey,
      data: Seq[DataEntry[_]],
      fee: TxAmount,
      timestamp: TxTimestamp,
      proofs: Proofs,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, DataTransaction] =
    DataTransaction(version, sender, data, fee, timestamp, proofs, chainId).validatedEither

  def signed(
      version: TxVersion,
      sender: PublicKey,
      data: Seq[DataEntry[_]],
      fee: TxAmount,
      timestamp: TxTimestamp,
      signer: PrivateKey
  ): Either[ValidationError, DataTransaction] =
    create(version, sender, data, fee, timestamp, Proofs.empty).map(_.signWith(signer))

  def selfSigned(
      version: TxVersion,
      sender: KeyPair,
      data: Seq[DataEntry[_]],
      fee: TxAmount,
      timestamp: TxTimestamp
  ): Either[ValidationError, DataTransaction] =
    signed(version, sender.publicKey, data, fee, timestamp, sender.privateKey)
}
