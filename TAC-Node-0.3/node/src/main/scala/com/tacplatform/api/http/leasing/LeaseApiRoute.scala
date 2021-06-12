package com.tacplatform.api.http.leasing

import akka.http.scaladsl.server.Route
import com.tacplatform.api.common.{CommonAccountsApi, LeaseInfo}
import com.tacplatform.api.http.{BroadcastRoute, _}
import com.tacplatform.api.http.requests.{LeaseCancelRequest, LeaseRequest}
import com.tacplatform.api.http.ApiError.{InvalidIds, TooBigArrayAllocation, TransactionDoesNotExist}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.Base58
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.network.TransactionPublisher
import com.tacplatform.settings.RestAPISettings
import com.tacplatform.state.Blockchain
import com.tacplatform.transaction._
import com.tacplatform.transaction.lease.LeaseTransaction
import com.tacplatform.utils.Time
import com.tacplatform.wallet.Wallet
import play.api.libs.json._

case class LeaseApiRoute(
    settings: RestAPISettings,
    wallet: Wallet,
    blockchain: Blockchain,
    transactionPublisher: TransactionPublisher,
    time: Time,
    commonAccountApi: CommonAccountsApi
) extends ApiRoute
    with BroadcastRoute
    with AuthRoute {
  import LeaseApiRoute._

  override val route: Route = pathPrefix("leasing") {
    active ~ deprecatedRoute
  }

  private def deprecatedRoute: Route =
    (path("lease") & withAuth) {
      broadcast[LeaseRequest](TransactionFactory.lease(_, wallet, time))
    } ~ (path("cancel") & withAuth) {
      broadcast[LeaseCancelRequest](TransactionFactory.leaseCancel(_, wallet, time))
    } ~ pathPrefix("broadcast") {
      path("lease")(broadcast[LeaseRequest](_.toTx)) ~
        path("cancel")(broadcast[LeaseCancelRequest](_.toTx))
    } ~ pathPrefix("info")(leaseInfo)

  private[this] def active: Route = (pathPrefix("active") & get & extractScheduler) { implicit sc =>
    path(AddrSegment) { address =>
      val leaseInfoJson =
        if (blockchain.isFeatureActivated(BlockchainFeatures.SynchronousCalls))
          commonAccountApi.activeLeases(address).map(Json.toJson(_))
        else
          commonAccountApi
            .activeLeasesOld(address)
            .collect {
              case (height, leaseTransaction: LeaseTransaction) =>
                leaseTransaction.json() + ("height" -> JsNumber(height))
            }

      complete(leaseInfoJson.toListL.runToFuture)
    }
  }

  private[this] def leaseInfo: Route =
    (get & path(TransactionId)) { leaseId =>
      val result = commonAccountApi
        .leaseInfo(leaseId)
        .toRight(TransactionDoesNotExist)

      complete(result)
    } ~ anyParam("id") { ids =>
      if (ids.size > settings.transactionsByAddressLimit)
        complete(TooBigArrayAllocation(settings.transactionsByAddressLimit))
      else
        leasingInfosMap(ids) match {
          case Left(err) => complete(err)
          case Right(leaseInfoByIdMap) =>
            val results = ids.map(leaseInfoByIdMap).toVector
            complete(results)
        }
    }

  private[this] def leasingInfosMap(ids: Iterable[String]): Either[InvalidIds, Map[String, LeaseInfo]] = {
    val infos = ids.map(
      id =>
        (for {
          id <- Base58.tryDecodeWithLimit(id).toOption
          li <- commonAccountApi.leaseInfo(ByteStr(id))
        } yield li).toRight(id)
    )
    val failed = infos.flatMap(_.left.toOption)

    if (failed.isEmpty) {
      Right(infos.collect {
        case Right(li) => li.id.toString -> li
      }.toMap)
    } else {
      Left(InvalidIds(failed.toVector))
    }
  }
}

object LeaseApiRoute {
  implicit val leaseStatusWrites: Writes[LeaseInfo.Status] =
    Writes(s => JsString(s.toString.toLowerCase))

  implicit val leaseInfoWrites: OWrites[LeaseInfo] = {
    import com.tacplatform.utils.byteStrFormat
    Json.writes[LeaseInfo]
  }
}
