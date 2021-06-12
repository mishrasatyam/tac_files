package com.tacplatform

import cats.data.ValidatedNel
import com.tacplatform.account.PrivateKey
import com.tacplatform.block.{Block, MicroBlock}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.state.Diff
import com.tacplatform.transaction.validation.TxValidator
import com.tacplatform.utils.base58Length

package object transaction {
  val AssetIdLength: Int       = com.tacplatform.crypto.DigestLength
  val AssetIdStringLength: Int = base58Length(AssetIdLength)

  type DiscardedBlocks       = Seq[(Block, ByteStr)]
  type DiscardedMicroBlocks  = Seq[(MicroBlock, Diff)]
  type AuthorizedTransaction = Authorized with Transaction

  type TxType = Byte

  type TxVersion = Byte
  object TxVersion {
    val V1: TxVersion = 1.toByte
    val V2: TxVersion = 2.toByte
    val V3: TxVersion = 3.toByte
  }
  type TxAmount    = Long
  type TxTimestamp = Long
  type TxByteArray = Array[Byte]

  implicit class TransactionValidationOps[T <: Transaction](val tx: T) extends AnyVal {
    def validatedNel(implicit validator: TxValidator[T]): ValidatedNel[ValidationError, T] = validator.validate(tx)
    def validatedEither(implicit validator: TxValidator[T]): Either[ValidationError, T]    = this.validatedNel.toEither.left.map(_.head)
  }

  implicit class TransactionSignOps[T](val tx: T) extends AnyVal {
    def signWith(privateKey: PrivateKey)(implicit sign: (T, PrivateKey) => T): T = sign(tx, privateKey)
  }
}
