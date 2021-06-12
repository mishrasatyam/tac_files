package com.tacplatform

import com.google.protobuf.ByteString
import com.tacplatform.account.{Address, AddressScheme, PublicKey}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.protobuf.transaction.PBRecipients

package object protobuf {
  implicit class ByteStrExt(val bs: ByteStr) extends AnyVal {
    def toByteString: ByteString = ByteString.copyFrom(bs.arr)
  }

  implicit class AddressExt(val a: Address) extends AnyVal {
    def toByteString: ByteString = ByteString.copyFrom(a.bytes)
  }

  implicit class ByteStringExt(val bs: ByteString) extends AnyVal {
    def toByteStr: ByteStr     = ByteStr(bs.toByteArray)
    def toPublicKey: PublicKey = PublicKey(bs.toByteArray)
    def toAddress: Address =
      PBRecipients
        .toAddress(bs.toByteArray, AddressScheme.current.chainId)
        .fold(ve => throw new IllegalArgumentException(ve.toString), identity)
  }
}
