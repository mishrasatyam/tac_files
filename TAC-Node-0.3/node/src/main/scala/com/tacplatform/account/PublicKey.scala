package com.tacplatform.account

import com.google.common.collect.Interners
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.Base58
import com.tacplatform.crypto._
import com.tacplatform.transaction.TxValidationError.InvalidAddress
import com.tacplatform.utils.base58Length
import play.api.libs.json.{Format, Writes}
import supertagged._

object PublicKey extends TaggedType[ByteStr] {
  private[this] val interner = Interners.newWeakInterner[PublicKey]()

  val KeyStringLength: Int = base58Length(KeyLength)

  def apply(publicKey: ByteStr): PublicKey = {
    require(publicKey.arr.length == KeyLength, s"invalid public key length: ${publicKey.arr.length}")
    interner.intern(publicKey @@ this)
  }

  def apply(publicKey: Array[Byte]): PublicKey =
    apply(ByteStr(publicKey))

  def fromBase58String(base58: String): Either[InvalidAddress, PublicKey] =
    (for {
      _     <- Either.cond(base58.length <= KeyStringLength, (), "Bad public key string length")
      bytes <- Base58.tryDecodeWithLimit(base58).toEither.left.map(ex => s"Unable to decode base58: ${ex.getMessage}")
    } yield PublicKey(bytes)).left.map(err => InvalidAddress(s"Invalid sender: $err"))

  def unapply(arg: Array[Byte]): Option[PublicKey] =
    Some(apply(arg))

  implicit class PublicKeyImplicitOps(private val pk: PublicKey) extends AnyVal {
    def toAddress: Address                = Address.fromPublicKey(pk)
    def toAddress(chainId: Byte): Address = Address.fromPublicKey(pk, chainId)
  }

  implicit lazy val jsonFormat: Format[PublicKey] = Format[PublicKey](
    com.tacplatform.utils.byteStrFormat.map(this.apply),
    Writes(pk => com.tacplatform.utils.byteStrFormat.writes(pk))
  )
}
