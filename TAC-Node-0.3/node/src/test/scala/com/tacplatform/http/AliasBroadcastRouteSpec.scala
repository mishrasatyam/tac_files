package com.tacplatform.http

import com.tacplatform.api.common.CommonTransactionsApi
import com.tacplatform.api.http.ApiError._
import com.tacplatform.api.http._
import com.tacplatform.api.http.alias.AliasApiRoute
import com.tacplatform.state.Blockchain
import com.tacplatform.state.diffs.TransactionDiffer.TransactionValidationError
import com.tacplatform.transaction.Transaction
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.utils.Time
import com.tacplatform.wallet.Wallet
import com.tacplatform.{NoShrink, RequestGen}
import org.scalamock.scalatest.PathMockFactory
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}
import play.api.libs.json.Json._
import play.api.libs.json._

class AliasBroadcastRouteSpec
    extends RouteSpec("/alias/broadcast/")
    with RequestGen
    with PathMockFactory
    with PropertyChecks
    with RestAPISettingsHelper
    with NoShrink {
  private[this] val utxPoolSynchronizer = DummyTransactionPublisher.rejecting(tx => TransactionValidationError(GenericError("foo"), tx))

  val route = AliasApiRoute(restAPISettings, stub[CommonTransactionsApi], stub[Wallet], utxPoolSynchronizer, stub[Time], stub[Blockchain]).route

  "returns StateCheckFiled" - {

    def posting(url: String, v: JsValue): RouteTestResult = Post(routePath(url), v) ~> route

    "when state validation fails" in {
      forAll(createAliasGen.retryUntil(_.version == 1)) { t: Transaction =>
        posting("create", t.json()) should produce(StateCheckFailed(t, "foo"))
      }
    }
  }

  "returns appropriate error code when validation fails for" - {

    "create alias transaction" in forAll(createAliasReq) { req =>
      import com.tacplatform.api.http.requests.SignedCreateAliasV1Request.jsonFormat

      def posting(v: JsValue): RouteTestResult = Post(routePath("create"), v) ~> route

      forAll(invalidBase58) { s =>
        posting(toJson(req.copy(senderPublicKey = s))) should produce(InvalidAddress)
      }
      forAll(nonPositiveLong) { q =>
        posting(toJson(req.copy(fee = q))) should produce(InsufficientFee())
      }
      forAll(invalidAliasStringByLength) { q =>
        val obj = toJson(req).as[JsObject] ++ Json.obj("alias" -> JsString(q))
        posting(obj) should produce(CustomValidationError(s"Alias '$q' length should be between 4 and 30"))
      }
    }
  }
}
