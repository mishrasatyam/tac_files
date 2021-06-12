package com.tacplatform.state

import com.tacplatform.account.Address
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.crypto.SignatureLength
import com.tacplatform.db.WithDomain
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.{NoShrink, TestTime, TransactionGen}
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class CommonSpec extends FreeSpec with Matchers with WithDomain with TransactionGen with PropertyChecks with NoShrink {
  private val time          = new TestTime
  private def nextTs        = time.getTimestamp()
  private val AssetIdLength = 32

  private def genesisBlock(genesisTs: Long, address: Address, initialBalance: Long) = TestBlock.create(
    genesisTs,
    ByteStr(Array.fill[Byte](SignatureLength)(0)),
    Seq(GenesisTransaction.create(address, initialBalance, genesisTs).explicitGet())
  )

  "Common Conditions" - {
    "Zero balance of absent asset" in forAll(accountGen, positiveLongGen, byteArrayGen(AssetIdLength)) {
      case (sender, initialBalance, assetId) =>
        withDomain() { d =>
          d.appendBlock(genesisBlock(nextTs, sender.toAddress, initialBalance))
          d.balance(sender.toAddress, IssuedAsset(ByteStr(assetId))) shouldEqual 0L
        }
    }
  }
}
