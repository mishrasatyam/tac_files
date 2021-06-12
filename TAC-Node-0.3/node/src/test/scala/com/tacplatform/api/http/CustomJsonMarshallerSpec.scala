package com.tacplatform.api.http

import scala.reflect.ClassTag

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.tacplatform.{NoShrink, NTPTime, TestWallet, TransactionGen}
import com.tacplatform.api.common.{CommonAccountsApi, CommonAssetsApi, CommonTransactionsApi}
import com.tacplatform.api.common.CommonTransactionsApi.TransactionMeta
import com.tacplatform.api.http.assets.AssetsApiRoute
import com.tacplatform.common.state.ByteStr
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.history.DefaultBlockchainSettings
import com.tacplatform.http.{ApiErrorMatchers, RestAPISettingsHelper}
import com.tacplatform.network.TransactionPublisher
import com.tacplatform.state.{Blockchain, Height}
import com.tacplatform.state.reader.LeaseDetails
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.Asset.IssuedAsset
import com.tacplatform.utx.UtxPool
import org.scalactic.source.Position
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json._

class CustomJsonMarshallerSpec
    extends PropSpec
    with RestAPISettingsHelper
    with PathMockFactory
    with TestWallet
    with NTPTime
    with ScalatestRouteTest
    with Matchers
    with ApiErrorMatchers
    with NoShrink
    with ScalaCheckPropertyChecks
    with TransactionGen {
  private val blockchain      = mock[Blockchain]
  private val utx             = mock[UtxPool]
  private val publisher       = mock[TransactionPublisher]
  private val transactionsApi = mock[CommonTransactionsApi]
  private val accountsApi     = mock[CommonAccountsApi]
  private val assetsApi       = mock[CommonAssetsApi]

  private val numberFormat = Accept(`application/json`.withParams(Map("large-significand-format" -> "string")))

  (() => blockchain.activatedFeatures).expects().returning(BlockchainFeatures.implemented.map(_ -> 0).toMap).anyNumberOfTimes()
  (() => blockchain.settings).expects().returning(DefaultBlockchainSettings).anyNumberOfTimes()

  private def ensureFieldsAre[A: ClassTag](v: JsObject, fields: String*)(implicit pos: Position): Unit =
    for (f <- fields) (v \ f).get shouldBe a[A]

  private def checkRoute(req: HttpRequest, route: Route, fields: String*)(implicit pos: Position): Unit = {
    req ~> route ~> check {
      ensureFieldsAre[JsNumber](responseAs[JsObject], fields: _*)
    }

    req ~> numberFormat ~> route ~> check {
      ensureFieldsAre[JsString](responseAs[JsObject], fields: _*)
    }
  }

  private val transactionsRoute =
    TransactionsApiRoute(restAPISettings, transactionsApi, testWallet, blockchain, () => utx.size, publisher, ntpTime).route

  property("/transactions/info/{id}") {
    forAll(leaseGen) { lt =>
      val height: Height = Height(1)
      (transactionsApi.transactionById _).expects(lt.id()).returning(Some(TransactionMeta.Default(height, lt, succeeded = true))).twice()
      (blockchain.leaseDetails _)
        .expects(lt.id())
        .returning(Some(LeaseDetails(lt.sender, lt.recipient, lt.amount, LeaseDetails.Status.Active, lt.id(), 1)))
        .twice()
      checkRoute(Get(s"/transactions/info/${lt.id()}"), transactionsRoute, "amount")
    }
  }

  property("/transactions/calculateFee") {
    (() => blockchain.height).expects().returning(1000).anyNumberOfTimes()
    (blockchain.assetScript _).expects(*).returning(None).anyNumberOfTimes()

    forAll(transferV2Gen) { tx =>
      (transactionsApi.calculateFee _).expects(*).returning(Right((Asset.Tac, 1, 1))).twice()
      checkRoute(Post("/transactions/calculateFee", tx.json()), transactionsRoute, "feeAmount")
    }
  }

  private val rewardRoute = RewardApiRoute(blockchain).route

  property("/blockchain/rewards") {
    (() => blockchain.height).expects().returning(1000).anyNumberOfTimes()
    (blockchain.blockReward _).expects(*).returning(Some(1000)).twice()
    (blockchain.tacAmount _).expects(*).returning(BigInt(10000000)).twice()
    (blockchain.blockRewardVotes _).expects(1000).returning(Seq(100L)).twice()

    checkRoute(Get("/blockchain/rewards/1000"), rewardRoute, "totalTacAmount", "currentReward", "minIncrement")
  }

  property("/debug/stateTac") {
    pending // todo: fix when distributions/portfolio become testable
  }

  private val assetsRoute = AssetsApiRoute(restAPISettings, testWallet, publisher, blockchain, ntpTime, accountsApi, assetsApi, 1000).route

  property("/assets/{assetId}/distribution/{height}/limit/{limit}") {
    pending // todo: fix when distributions/portfolio become testable
  }

  property("/assets/balance/{address}/{assetId}") {
    forAll(accountGen, bytes32gen.map(b => IssuedAsset(ByteStr(b)))) {
      case (keyPair, assetId) =>
        (blockchain.balance _).expects(keyPair.toAddress, assetId).returning(1000L).twice()
        checkRoute(Get(s"/assets/balance/${keyPair.publicKey.toAddress}/${assetId.id}"), assetsRoute, "balance")
    }
  }
}
