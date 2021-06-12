package com.tacplatform.protobuf

package object transaction {
  type PBOrder = com.tacplatform.protobuf.order.Order
  val PBOrder = com.tacplatform.protobuf.order.Order

  type VanillaOrder = com.tacplatform.transaction.assets.exchange.Order
  val VanillaOrder = com.tacplatform.transaction.assets.exchange.Order

  type PBTransaction = com.tacplatform.protobuf.transaction.Transaction
  val PBTransaction = com.tacplatform.protobuf.transaction.Transaction

  type PBSignedTransaction = com.tacplatform.protobuf.transaction.SignedTransaction
  val PBSignedTransaction = com.tacplatform.protobuf.transaction.SignedTransaction

  type VanillaTransaction = com.tacplatform.transaction.Transaction
  val VanillaTransaction = com.tacplatform.transaction.Transaction

  type VanillaSignedTransaction = com.tacplatform.transaction.SignedTransaction

  type VanillaAssetId = com.tacplatform.transaction.Asset
}
