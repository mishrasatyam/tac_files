package com.tacplatform.it.sync.grpc

import com.tacplatform.common.utils.{Base64, EitherExt2}
import com.tacplatform.it.NTPTime
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.it.sync.{matcherFee, minFee, someAssetAmount}
import com.tacplatform.it.util._
import com.tacplatform.protobuf.transaction.{PBTransactions, Recipient}
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.TxVersion
import com.tacplatform.transaction.assets.IssueTransaction
import com.tacplatform.transaction.assets.exchange.{AssetPair, Order}
import com.tacplatform.utils._
import io.grpc.Status.Code

import scala.collection.immutable

class ExchangeTransactionGrpcSuite extends GrpcBaseTransactionSuite with NTPTime {

  val transactionV1versions: (TxVersion, TxVersion, TxVersion) = (1: Byte, 1: Byte, 1: Byte)
  val transactionV2versions: immutable.Seq[(TxVersion, TxVersion, TxVersion)] = for {
    o1ver <- 1 to 3
    o2ver <- 1 to 3
    txVer <- 2 to 3
  } yield (o1ver.toByte, o2ver.toByte, txVer.toByte)

  val (buyer, buyerAddress)     = (firstAcc, firstAddress)
  val (seller, sellerAddress)   = (secondAcc, secondAddress)
  val (matcher, matcherAddress) = (thirdAcc, thirdAddress)

  val versions: immutable.Seq[(TxVersion, TxVersion, TxVersion)] = transactionV1versions +: transactionV2versions

  test("exchange tx with orders v1,v2") {
    val exchAsset          = sender.broadcastIssue(buyer, Base64.encode("exchAsset".utf8Bytes), someAssetAmount, 8, reissuable = true, 1.tac, waitForTx = true)
    val exchAssetId        = PBTransactions.vanilla(exchAsset).explicitGet().id().toString
    val price              = 500000L
    val amount             = 40000000L
    val priceAssetSpending = amount * price / 100000000L
    val pair               = AssetPair.createAssetPair("TAC", exchAssetId).get
    for ((o1ver, o2ver, tver) <- versions) {
      val ts                       = ntpTime.correctedTime()
      val expirationTimestamp      = ts + Order.MaxLiveTime
      val buy                      = Order.buy(o1ver, buyer, matcher.publicKey, pair, amount, price, ts, expirationTimestamp, matcherFee)
      val sell                     = Order.sell(o2ver, seller, matcher.publicKey, pair, amount, price, ts, expirationTimestamp, matcherFee)
      val buyerTacBalanceBefore  = sender.tacBalance(buyerAddress).available
      val sellerTacBalanceBefore = sender.tacBalance(sellerAddress).available
      val buyerAssetBalanceBefore  = sender.assetsBalance(buyerAddress, Seq(exchAssetId)).getOrElse(exchAssetId, 0L)
      val sellerAssetBalanceBefore = sender.assetsBalance(sellerAddress, Seq(exchAssetId)).getOrElse(exchAssetId, 0L)

      sender.exchange(matcher, buy, sell, amount, price, matcherFee, matcherFee, matcherFee, ts, tver, waitForTx = true)

      sender.tacBalance(buyerAddress).available shouldBe buyerTacBalanceBefore + amount - matcherFee
      sender.tacBalance(sellerAddress).available shouldBe sellerTacBalanceBefore - amount - matcherFee
      sender.assetsBalance(buyerAddress, Seq(exchAssetId))(exchAssetId) shouldBe buyerAssetBalanceBefore - priceAssetSpending
      sender.assetsBalance(sellerAddress, Seq(exchAssetId))(exchAssetId) shouldBe sellerAssetBalanceBefore + priceAssetSpending
    }
  }

  test("exchange tx with orders v3") {
    val feeAsset           = sender.broadcastIssue(buyer, "feeAsset", someAssetAmount, 8, reissuable = true, 1.tac, waitForTx = true)
    val feeAssetId         = PBTransactions.vanilla(feeAsset).explicitGet().id()
    val price              = 500000L
    val amount             = 40000000L
    val priceAssetSpending = price * amount / 100000000L

    sender.broadcastTransfer(
      buyer,
      Recipient().withPublicKeyHash(sellerAddress),
      someAssetAmount / 2,
      minFee,
      assetId = feeAssetId.toString,
      waitForTx = true
    )

    for ((o1ver, o2ver, matcherFeeOrder1, matcherFeeOrder2, buyerTacDelta, sellerTacDelta, buyerAssetDelta, sellerAssetDelta) <- Seq(
           (1: Byte, 3: Byte, Tac, IssuedAsset(feeAssetId), amount - matcherFee, -amount, -priceAssetSpending, priceAssetSpending - matcherFee),
           (1: Byte, 3: Byte, Tac, Tac, amount - matcherFee, -amount - matcherFee, -priceAssetSpending, priceAssetSpending),
           (2: Byte, 3: Byte, Tac, IssuedAsset(feeAssetId), amount - matcherFee, -amount, -priceAssetSpending, priceAssetSpending - matcherFee),
           (3: Byte, 1: Byte, IssuedAsset(feeAssetId), Tac, amount, -amount - matcherFee, -priceAssetSpending - matcherFee, priceAssetSpending),
           (2: Byte, 3: Byte, Tac, Tac, amount - matcherFee, -amount - matcherFee, -priceAssetSpending, priceAssetSpending),
           (3: Byte, 2: Byte, IssuedAsset(feeAssetId), Tac, amount, -amount - matcherFee, -priceAssetSpending - matcherFee, priceAssetSpending)
         )) {
      if (matcherFeeOrder1 == Tac && matcherFeeOrder2 != Tac) {
        sender.broadcastTransfer(
          buyer,
          Recipient().withPublicKeyHash(sellerAddress),
          100000,
          minFee,
          assetId = feeAssetId.toString,
          waitForTx = true
        )
      }

      val buyerTacBalanceBefore  = sender.tacBalance(buyerAddress).available
      val sellerTacBalanceBefore = sender.tacBalance(sellerAddress).available
      val buyerAssetBalanceBefore  = sender.assetsBalance(buyerAddress, Seq(feeAssetId.toString)).getOrElse(feeAssetId.toString, 0L)
      val sellerAssetBalanceBefore = sender.assetsBalance(sellerAddress, Seq(feeAssetId.toString)).getOrElse(feeAssetId.toString, 0L)

      val ts                  = ntpTime.correctedTime()
      val expirationTimestamp = ts + Order.MaxLiveTime
      val assetPair           = AssetPair.createAssetPair("TAC", feeAssetId.toString).get
      val buy                 = Order.buy(o1ver, buyer, matcher.publicKey, assetPair, amount, price, ts, expirationTimestamp, matcherFee, matcherFeeOrder1)
      val sell                = Order.sell(o2ver, seller, matcher.publicKey, assetPair, amount, price, ts, expirationTimestamp, matcherFee, matcherFeeOrder2)

      sender.exchange(matcher, sell, buy, amount, price, matcherFee, matcherFee, matcherFee, ts, 3, waitForTx = true)

      sender.tacBalance(buyerAddress).available shouldBe (buyerTacBalanceBefore + buyerTacDelta)
      sender.tacBalance(sellerAddress).available shouldBe (sellerTacBalanceBefore + sellerTacDelta)
      sender.assetsBalance(buyerAddress, Seq(feeAssetId.toString))(feeAssetId.toString) shouldBe (buyerAssetBalanceBefore + buyerAssetDelta)
      sender.assetsBalance(sellerAddress, Seq(feeAssetId.toString))(feeAssetId.toString) shouldBe (sellerAssetBalanceBefore + sellerAssetDelta)
    }
  }

  test("cannot exchange non-issued assets") {
    val exchAsset: IssueTransaction = IssueTransaction(
      TxVersion.V1,
      sender.publicKey,
      "myasset".utf8Bytes,
      "my asset description".utf8Bytes,
      quantity = someAssetAmount,
      decimals = 2,
      reissuable = true,
      script = None,
      fee = 1.tac,
      timestamp = System.currentTimeMillis()
    ).signWith(sender.keyPair.privateKey)
    for ((o1ver, o2ver, tver) <- versions) {

      val assetId             = exchAsset.id().toString
      val ts                  = ntpTime.correctedTime()
      val expirationTimestamp = ts + Order.MaxLiveTime
      val price               = 2 * Order.PriceConstant
      val amount              = 1
      val pair                = AssetPair.createAssetPair("TAC", assetId).get
      val buy                 = Order.buy(o1ver, buyer, matcher.publicKey, pair, amount, price, ts, expirationTimestamp, matcherFee)
      val sell                = Order.sell(o2ver, seller, matcher.publicKey, pair, amount, price, ts, expirationTimestamp, matcherFee)

      assertGrpcError(
        sender.exchange(matcher, buy, sell, amount, price, matcherFee, matcherFee, matcherFee, ts, tver),
        "Assets should be issued before they can be traded",
        Code.INVALID_ARGUMENT
      )
    }
  }
}
