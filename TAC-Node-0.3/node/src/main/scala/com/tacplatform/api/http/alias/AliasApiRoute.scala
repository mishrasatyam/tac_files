package com.tacplatform.api.http.alias

import akka.NotUsed
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import cats.syntax.either._
import com.tacplatform.account.Alias
import com.tacplatform.api.common.CommonTransactionsApi
import com.tacplatform.api.http.requests.CreateAliasRequest
import com.tacplatform.api.http.{BroadcastRoute, _}
import com.tacplatform.network.TransactionPublisher
import com.tacplatform.settings.RestAPISettings
import com.tacplatform.state.Blockchain
import com.tacplatform.transaction._
import com.tacplatform.utils.Time
import com.tacplatform.wallet.Wallet
import play.api.libs.json.{JsString, JsValue, Json}

case class AliasApiRoute(
    settings: RestAPISettings,
    commonApi: CommonTransactionsApi,
    wallet: Wallet,
    transactionPublisher: TransactionPublisher,
    time: Time,
    blockchain: Blockchain
) extends ApiRoute
    with BroadcastRoute
    with AuthRoute {

  override val route: Route = pathPrefix("alias") {
    addressOfAlias ~ aliasOfAddress ~ deprecatedRoute
  }

  private def deprecatedRoute: Route =
    path("broadcast" / "create") {
      broadcast[CreateAliasRequest](_.toTx)
    } ~ (path("create") & withAuth) {
      broadcast[CreateAliasRequest](TransactionFactory.createAlias(_, wallet, time))
    }

  def addressOfAlias: Route = (get & path("by-alias" / Segment)) { aliasName =>
    complete {
      Alias
        .create(aliasName)
        .flatMap { a =>
          blockchain.resolveAlias(a).bimap(_ => TxValidationError.AliasDoesNotExist(a), addr => Json.obj("address" -> addr.stringRepr))
        }
    }
  }

  private implicit val ess: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def aliasOfAddress: Route = (get & path("by-address" / AddrSegment)) { address =>
    extractScheduler { implicit s =>
      val value: Source[JsValue, NotUsed] =
        Source.fromPublisher(commonApi.aliasesOfAddress(address).map { case (_, tx) => JsString(tx.alias.stringRepr) }.toReactivePublisher)
      complete(value)
    }
  }
}
