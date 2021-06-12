package com.tacplatform.api.http

import akka.http.scaladsl.server.Route
import com.tacplatform.common.utils.Base58
import com.tacplatform.settings.RestAPISettings
import com.tacplatform.wallet.Wallet
import play.api.libs.json.Json

case class WalletApiRoute(settings: RestAPISettings, wallet: Wallet) extends ApiRoute with AuthRoute {
  override lazy val route: Route = seed

  def seed: Route = (path("wallet" / "seed") & get & withAuth) {
    complete(Json.obj("seed" -> Base58.encode(wallet.seed)))
  }
}
