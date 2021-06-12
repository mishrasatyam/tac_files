package com.tacplatform.protobuf.utils

import com.tacplatform.lang.ValidationError
import com.tacplatform.protobuf.{Amount, _}
import com.tacplatform.protobuf.transaction._
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}

object PBImplicitConversions {
  import com.google.protobuf.{ByteString => PBByteString}
  import com.tacplatform.account.{AddressOrAlias, Address => VAddress, Alias => VAlias}

  implicit def fromAddressOrAlias(addressOrAlias: AddressOrAlias): Recipient = PBRecipients.create(addressOrAlias)
  implicit def fromAddress(address: VAddress): PBByteString                  = PBByteString.copyFrom(address.bytes)

  implicit class PBRecipientImplicitConversionOps(val recipient: Recipient) extends AnyVal {
    def toAddress(chainId: Byte): Either[ValidationError, VAddress]              = PBRecipients.toAddress(recipient, chainId)
    def toAlias(chainId: Byte): Either[ValidationError, VAlias]                  = PBRecipients.toAlias(recipient, chainId)
    def toAddressOrAlias(chainId: Byte): Either[ValidationError, AddressOrAlias] = PBRecipients.toAddressOrAlias(recipient, chainId)
  }

  implicit def fromAssetIdAndAmount(v: (VanillaAssetId, Long)): Amount = v match {
    case (IssuedAsset(assetId), amount) =>
      Amount()
        .withAssetId(assetId.toByteString)
        .withAmount(amount)

    case (Tac, amount) =>
      Amount().withAmount(amount)
  }

  implicit class AmountImplicitConversions(val a: Amount) extends AnyVal {
    def longAmount: Long      = a.amount
    def vanillaAssetId: Asset = PBAmounts.toVanillaAssetId(a.assetId)
  }
}
