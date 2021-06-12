package com.tacplatform.transaction

trait VersionedTransaction {
  def version: TxVersion
}
