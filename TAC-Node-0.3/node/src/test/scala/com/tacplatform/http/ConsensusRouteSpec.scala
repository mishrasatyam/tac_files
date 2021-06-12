package com.tacplatform.http

import akka.http.scaladsl.server.Route
import com.tacplatform.BlockGen
import com.tacplatform.api.http.ApiError.BlockDoesNotExist
import com.tacplatform.api.http.ApiMarshallers._
import com.tacplatform.consensus.nxt.api.http.NxtConsensusApiRoute
import com.tacplatform.db.WithDomain
import com.tacplatform.state._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}
import play.api.libs.json.JsObject

class ConsensusRouteSpec
    extends RouteSpec("/consensus")
    with RestAPISettingsHelper
    with PropertyChecks
    with BlockGen
    with HistoryTest
    with WithDomain {

  private def routeTest(f: (Blockchain, Route) => Any) = withDomain() { d =>
    d.blockchainUpdater.processBlock(genesisBlock, genesisBlock.header.generationSignature)
    1 to 10 foreach { _ =>
      val block = getNextTestBlock(d.blockchainUpdater)
      d.blockchainUpdater.processBlock(block, block.header.generationSignature)
    }
    f(d.blockchainUpdater, NxtConsensusApiRoute(restAPISettings, d.blockchainUpdater).route)
  }

  routePath("/basetarget") - {
    "for existing block" in routeTest { (h, route) =>
      val sh = h.blockHeader(3).get
      Get(routePath(s"/basetarget/${sh.id()}")) ~> route ~> check {
        (responseAs[JsObject] \ "baseTarget").as[Long] shouldEqual sh.header.baseTarget
      }
    }

    "for non-existent block" in routeTest { (h, route) =>
      Get(routePath(s"/basetarget/24aTK4mg6DMFKw4SuQCfSRG52MXg8DSjDWQopahs38Cm3tPMFM1m6fGqCoPY69kstM7TE4mpJAMYmG7LWTTjndCH")) ~> route should produce(BlockDoesNotExist)
    }
  }
}
