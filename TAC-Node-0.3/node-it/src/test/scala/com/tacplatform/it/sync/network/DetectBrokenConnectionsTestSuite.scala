package com.tacplatform.it.sync.network

import com.typesafe.config.{Config, ConfigFactory}
import com.tacplatform.it.NodeConfigs.Default
import com.tacplatform.it.ReportingTestName
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.transactions.NodesFromDocker
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._

class DetectBrokenConnectionsTestSuite extends FreeSpec with Matchers with ReportingTestName with NodesFromDocker {

  override protected def nodeConfigs: Seq[Config] = {
    val highPriorityConfig = ConfigFactory.parseString("tac.network.break-idle-connections-timeout = 20s")
    Default.take(2).map(highPriorityConfig.withFallback)
  }

  "disconnect nodes from the network and wait a timeout for detecting of broken connections" in {
    dockerNodes().foreach(docker.disconnectFromNetwork)
    Thread.sleep(30.seconds.toMillis)

    dockerNodes().foreach { node =>
      docker.connectToNetwork(Seq(node))
      node.connectedPeers shouldBe empty
      docker.disconnectFromNetwork(node)
    }

    // To prevent errors in the log
    docker.connectToNetwork(dockerNodes())
  }

}
