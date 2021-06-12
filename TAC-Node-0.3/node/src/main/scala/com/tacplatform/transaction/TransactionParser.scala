package com.tacplatform.transaction

import com.tacplatform.transaction.validation.TxValidator

import scala.util.Try

trait TransactionParser {
  type TransactionT <: Transaction

  def typeId: TxType

  def supportedVersions: Set[TxVersion]

  def parseBytes(bytes: Array[Byte]): Try[TransactionT]

  implicit def validator: TxValidator[TransactionT]
}
