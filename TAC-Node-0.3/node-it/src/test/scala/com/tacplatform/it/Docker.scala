package com.tacplatform.it

import java.io.{FileOutputStream, IOException}
import java.net.{InetAddress, InetSocketAddress, URL}
import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections._
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Properties, List => JList, Map => JMap}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper
import com.google.common.primitives.Ints._
import com.spotify.docker.client.messages.EndpointConfig.EndpointIpamConfig
import com.spotify.docker.client.messages._
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import com.tacplatform.account.AddressScheme
import com.tacplatform.block.Block
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.AsyncHttpApi._
import com.tacplatform.it.util.GlobalTimer.{instance => timer}
import com.tacplatform.settings._
import com.tacplatform.utils.ScorexLogging
import monix.eval.Coeval
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.io.IOUtils
import org.asynchttpclient.Dsl._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, blocking}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Random, Try}

class Docker(suiteConfig: Config = empty, tag: String = "", enableProfiling: Boolean = false, imageName: String = Docker.NodeImageName)
    extends AutoCloseable
    with ScorexLogging {

  import Docker._

  private val http = asyncHttpClient(
    config()
      .setNettyTimer(timer)
      .setMaxConnections(18)
      .setMaxConnectionsPerHost(3)
      .setMaxRequestRetry(1)
      .setReadTimeout(10000)
      .setKeepAlive(false)
      .setRequestTimeout(10000)
  )

  private val client = DefaultDockerClient.fromEnv().build()

  private val nodes     = ConcurrentHashMap.newKeySet[DockerNode]()
  private val isStopped = new AtomicBoolean(false)

  dumpContainers(client.listContainers())
  sys.addShutdownHook {
    log.debug("Shutdown hook")
    close()
  }

  private val genesisOverride = Docker.genesisOverride

  // a random network in 10.x.x.x range
  val networkSeed = Random.nextInt(0x100000) << 4 | 0x0A000000
  // 10.x.x.x/28 network will accommodate up to 13 nodes
  private val networkPrefix = s"${InetAddress.getByAddress(toByteArray(networkSeed)).getHostAddress}/28"

  private val logDir: Coeval[Path] = Coeval.evalOnce {
    val r = Option(System.getProperty("tac.it.logging.dir"))
      .map(Paths.get(_))
      .getOrElse(Paths.get(System.getProperty("user.dir"), "logs", RunId, tag.replaceAll("""(\w)\w*\.""", "$1.")))

    Files.createDirectories(r)
    r
  }

  private def ipForNode(nodeId: Int) = InetAddress.getByAddress(toByteArray(nodeId & 0xF | networkSeed)).getHostAddress

  private lazy val tacNetwork: Network = {
    val id          = Random.nextInt(Int.MaxValue)
    val networkName = s"tac-$id"

    def network: Option[Network] =
      try {
        val networks = client.listNetworks(DockerClient.ListNetworksParam.byNetworkName(networkName))
        if (networks.isEmpty) None else Some(networks.get(0))
      } catch {
        case NonFatal(_) => network
      }

    def attempt(rest: Int): Network =
      try {
        network match {
          case Some(n) =>
            val ipam = n
              .ipam()
              .config()
              .asScala
              .map(n => s"subnet=${n.subnet()}, ip range=${n.ipRange()}")
              .mkString(", ")
            log.info(s"Network ${n.name()} (id: ${n.id()}) is created for $tag, ipam: $ipam")
            n
          case None =>
            log.debug(s"Creating network $networkName for $tag")
            // Specify the network manually because of race conditions: https://github.com/moby/moby/issues/20648
            val r = client.createNetwork(
              NetworkConfig
                .builder()
                .name(networkName)
                .ipam(
                  Ipam
                    .builder()
                    .driver("default")
                    .config(singletonList(IpamConfig.create(networkPrefix, networkPrefix, ipForNode(0xE))))
                    .build()
                )
                .checkDuplicate(true)
                .build()
            )
            Option(r.warnings()).foreach(log.warn(_))
            attempt(rest - 1)
        }
      } catch {
        case NonFatal(e) =>
          log.warn(s"Can not create a network for $tag", e)
          if (rest == 0) throw e else attempt(rest - 1)
      }

    attempt(5)
  }

  def createNetwork: Network = tacNetwork

  def startNodes(nodeConfigs: Seq[Config]): Seq[DockerNode] = {
    log.trace(s"Starting ${nodeConfigs.size} containers")
    val all = nodeConfigs.map(startNodeInternal(_))
    Await.result(
      Future.traverse(all)(_.waitForStartup()),
      5.minutes
    )
    all
  }

  def startNode(nodeConfig: Config, autoConnect: Boolean = true): DockerNode = {
    val node = startNodeInternal(nodeConfig, autoConnect)
    Await.result(node.waitForStartup(), 3.minutes)
    node
  }

  private def peersFor(nodeName: String): Seq[InetSocketAddress] = {
    nodes.asScala
      .filterNot(_.name == nodeName)
      .filterNot { node =>
        // Exclude disconnected
        client.inspectContainer(node.containerId).networkSettings().networks().isEmpty
      }
      .map(_.containerNetworkAddress)
      .toSeq
  }

  private def connectToAll(node: DockerNode): Future[Unit] = {
    def connectToOne(address: InetSocketAddress): Future[Unit] = {
      for {
        _              <- node.connect(address)
        _              <- Future(blocking(Thread.sleep(1.seconds.toMillis)))
        connectedPeers <- node.connectedPeers
        _ <- {
          val connectedAddresses = connectedPeers.map(_.address.replaceAll("""^.*/([\d\.]+).+$""", "$1")).sorted
          log.debug(s"Looking for ${address.getHostName} in $connectedAddresses")
          if (connectedAddresses.contains(address.getHostName)) Future.successful(())
          else {
            log.debug(s"Not found ${address.getHostName}, retrying")
            connectToOne(address)
          }
        }
      } yield ()
    }

    val seedAddresses = peersFor(node.name)
    if (seedAddresses.isEmpty)
      Future.successful(())
    else
      Future
        .traverse(seedAddresses)(connectToOne)
        .map(_ => ())
  }

  private def startNodeInternal(nodeConfig: Config, autoConnect: Boolean = true): DockerNode =
    try {
      val nodeName = nodeConfig.getString("tac.network.node-name")
      val peersOverrides = if (autoConnect) {
        val otherAddrs = peersFor(nodeName)

        ConfigFactory
          .parseMap(Map("known-peers" -> otherAddrs.map(addr => s"${addr.getHostString}:${addr.getPort}").asJava).asJava)
          .atPath("tac.network")
      } else ConfigFactory.empty()

      val overrides = peersOverrides
        .withFallback(nodeConfig)
        .withFallback(suiteConfig)
        .withFallback(genesisOverride)
        .withFallback(configTemplate)

      val actualConfig = overrides
        .withFallback(defaultApplication())
        .withFallback(defaultReference())
        .resolve()

      val networkPort = actualConfig.getString("tac.network.port")

      val nodeNumber = nodeName.replace("node", "").toInt
      val ip         = ipForNode(nodeNumber)

      val javaOptions = Option(System.getenv("CONTAINER_JAVA_OPTS")).getOrElse("")
      val configOverrides: String = {
        val ntpServer    = Option(System.getenv("NTP_SERVER")).fold("")(x => s"-Dtac.ntp-server=$x ")
        val maxCacheSize = Option(System.getenv("MAX_CACHE_SIZE")).fold("")(x => s"-Dtac.max-cache-size=$x ")

        var config = s"$javaOptions ${renderProperties(asProperties(overrides))} " +
          s"-Dlogback.stdout.level=TRACE -Dlogback.file.level=OFF -Dtac.network.declared-address=$ip:$networkPort $ntpServer $maxCacheSize"

        if (enableProfiling) {
          // https://www.yourkit.com/docs/java/help/startup_options.jsp
          config += s"-agentpath:/usr/local/YourKit-JavaProfiler-2019.8/bin/linux-x86-64/libyjpagent.so=port=$ProfilerPort,listen=all," +
            s"sampling,monitors,sessionname=TacNode,dir=$ContainerRoot/profiler,logdir=$ContainerRoot,onexit=snapshot "
        }

        config
      }

      val hostConfig = HostConfig
        .builder()
        .publishAllPorts(true)
        .build()

      val containerConfig = ContainerConfig
        .builder()
        .image(imageName)
        .networkingConfig(ContainerConfig.NetworkingConfig.create(Map(tacNetwork.name() -> endpointConfigFor(nodeName)).asJava))
        .hostConfig(hostConfig)
        .env(s"JAVA_OPTS=$configOverrides")
        .build()

      val containerId = {
        val jenkinsJobIdFromEnv = sys.env.get("JENKINS_JOB_ID").fold("")(s => s"-$s")
        val containerName       = s"${tacNetwork.name()}-$nodeName$jenkinsJobIdFromEnv"
        dumpContainers(
          client.listContainers(DockerClient.ListContainersParam.filter("name", containerName)),
          "Containers with same name"
        )

        log.debug(s"Creating container $containerName at $ip with options: $javaOptions")
        val r = client.createContainer(containerConfig, containerName)
        Option(r.warnings().asScala).toSeq.flatten.foreach(log.warn(_))
        r.id()
      }

      client.startContainer(containerId)

      val node = new DockerNode(actualConfig, containerId, getNodeInfo(containerId, TacSettings.fromRootConfig(actualConfig)))
      nodes.add(node)
      log.debug(s"Started $containerId -> ${node.name}: ${node.nodeInfo}")
      node
    } catch {
      case NonFatal(e) =>
        log.error("Can't start a container", e)
        dumpContainers(client.listContainers())
        throw e
    }

  private def getNodeInfo(containerId: String, settings: TacSettings): NodeInfo = {
    val restApiPort = settings.restAPISettings.port
    val networkPort = settings.networkSettings.bindAddress.getPort

    val containerInfo  = inspectContainer(containerId)
    val tacIpAddress = containerInfo.networkSettings().networks().get(tacNetwork.name()).ipAddress()

    NodeInfo(restApiPort, networkPort, tacIpAddress, containerInfo.networkSettings().ports())
  }

  private def inspectContainer(containerId: String): ContainerInfo = {
    val containerInfo = client.inspectContainer(containerId)
    if (containerInfo.networkSettings().networks().asScala.contains(tacNetwork.name())) containerInfo
    else {
      log.debug(s"Container $containerId has not connected to the network ${tacNetwork.name()} yet, retry")
      Thread.sleep(1000)
      inspectContainer(containerId)
    }
  }

  def stopContainer(node: DockerNode): String = {
    val id = node.containerId
    log.info(s"Stopping container with id: $id")
    client.stopContainer(node.containerId, 10)
    saveProfile(node)
    saveLog(node)
    val containerInfo = client.inspectContainer(node.containerId)
    log.debug(s"""Container information for ${node.name}:
                 |Exit code: ${containerInfo.state().exitCode()}
                 |Error: ${containerInfo.state().error()}
                 |Status: ${containerInfo.state().status()}
                 |OOM killed: ${containerInfo.state().oomKilled()}""".stripMargin)
    id
  }

  def printThreadDump(node: DockerNode): Unit = {
    val id = node.containerId
    log.info(s"Saving thread dump for: $id")
    client.killContainer(id, DockerClient.Signal.SIGQUIT)
  }

  def startContainer(id: String): Unit = {
    client.startContainer(id)
    nodes.asScala.find(_.containerId == id).foreach { node =>
      node.nodeInfo = getNodeInfo(node.containerId, node.settings)
    }
  }

  def killAndStartContainer(node: DockerNode): DockerNode = {
    val id = node.containerId
    log.info(s"Killing container with id: $id")
    client.killContainer(id, DockerClient.Signal.SIGINT)
    saveProfile(node)
    saveLog(node)
    client.startContainer(id)
    node.nodeInfo = getNodeInfo(node.containerId, node.settings)
    Await.result(
      node.waitForStartup().flatMap(_ => connectToAll(node)),
      3.minutes
    )
    node
  }

  def restartNode(node: DockerNode, configUpdates: Config = empty): DockerNode = {
    Await.result(node.waitForHeightArise, 3.minutes)

    if (configUpdates != empty) {
      val renderedConfig = renderProperties(asProperties(configUpdates))

      // Docker do not allow updating ENV https://github.com/moby/moby/issues/8838 :(
      log.debug("Set new config directly in the entrypoint.sh script")
      val shPath = "/usr/share/tac/bin/entrypoint.sh"
      val scriptCmd: Array[String] =
        Array("sh", "-c", s"sed -i 's|$${JAVA_OPTS}|$${JAVA_OPTS} $renderedConfig|' $shPath && cat $shPath")

      val execScriptCmd = client.execCreate(node.containerId, scriptCmd).id()
      client.execStart(execScriptCmd)
    }

    restartContainer(node)
  }

  override def close(): Unit = {
    if (isStopped.compareAndSet(false, true)) {
      log.info("Stopping containers")

      nodes.asScala.foreach { node =>
        client.stopContainer(node.containerId, if (enableProfiling) 60 else 0)
        log.debug(s"Container ${node.name} stopped with exit status: ${client.waitContainer(node.containerId).statusCode()}")

        saveProfile(node)
        saveLog(node)
        val containerInfo = client.inspectContainer(node.containerId)
        log.debug(s"""Container information for ${node.name}:
             |Exit code: ${containerInfo.state().exitCode()}
             |Error: ${containerInfo.state().error()}
             |Status: ${containerInfo.state().status()}
             |OOM killed: ${containerInfo.state().oomKilled()}""".stripMargin)

        try {
          client.removeContainer(node.containerId)
        } catch {
          case NonFatal(e) => log.warn(s"Can't remove a container of ${node.name}", e)
        }
      }

      try {
        client.removeNetwork(tacNetwork.id)
      } catch {
        case NonFatal(e) =>
          // https://github.com/moby/moby/issues/17217
          log.warn(s"Can not remove network ${tacNetwork.name()}", e)
      }

      http.close()
      client.close()
    }
  }

  private def saveLog(node: DockerNode): Unit = {
    val containerId = node.containerId
    val logFile     = logDir().resolve(s"${node.name}.log").toFile
    log.info(s"Writing logs of $containerId to ${logFile.getAbsolutePath}")

    val fileStream = new FileOutputStream(logFile, false)
    try {
      client
        .logs(
          containerId,
          DockerClient.LogsParam.follow(),
          DockerClient.LogsParam.stdout(),
          DockerClient.LogsParam.stderr()
        )
        .attach(fileStream, fileStream)
    } finally {
      fileStream.close()
    }
  }

  private def saveProfile(node: DockerNode): Unit = if (enableProfiling) {
    try {
      val profilerDirStream = client.archiveContainer(node.containerId, ContainerRoot.resolve("profiler").toString)

      try {
        val archiveStream = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, profilerDirStream)
        val snapshotFile = Iterator
          .continually(Option(archiveStream.getNextEntry))
          .takeWhile(_.nonEmpty)
          .collectFirst {
            case Some(entry: TarArchiveEntry) if entry.isFile && entry.getName.contains(".snapshot") => entry
          }

        snapshotFile.foreach { archiveFile =>
          val output = new FileOutputStream(logDir().resolve(s"${node.name}.snapshot").toFile)
          try {
            IOUtils.copy(archiveStream, output)
            log.info(s"The snapshot of ${node.name} was successfully saved")
          } catch {
            case e: Throwable => throw new IOException(s"Can't copy ${archiveFile.getName} of ${node.name} to local fs", e)
          } finally {
            output.close()
          }
        }
      } catch {
        case e: Throwable => throw new IOException(s"Can't read a profiler directory stream of ${node.name}", e)
      } finally {
        // Some kind of https://github.com/spotify/docker-client/issues/745
        // But we have to close this stream, otherwise the thread will be blocked
        Try(profilerDirStream.close())
      }
    } catch {
      case e: Throwable => log.warn(s"Can't save profiler logs of ${node.name}", e)
    }
  }

  def disconnectFromNetwork(node: DockerNode): Unit = disconnectFromNetwork(node.containerId)

  private def disconnectFromNetwork(containerId: String): Unit = {
    log.info(s"Trying to disconnect container $containerId from network ...")
    client.disconnectFromNetwork(containerId, tacNetwork.id())
  }

  def restartContainer(node: DockerNode): DockerNode = {
    val id            = node.containerId
    val containerInfo = inspectContainer(id)
    val ports         = containerInfo.networkSettings().ports()
    log.info(s"New ports: ${ports.toString}")
    client.restartContainer(id, 10)

    node.nodeInfo = getNodeInfo(node.containerId, node.settings)
    Await.result(
      node.waitForStartup().flatMap(_ => connectToAll(node)),
      3.minutes
    )
    node
  }

  def connectToNetwork(nodes: Seq[DockerNode]): Unit = {
    nodes.foreach(connectToNetwork)
    Await.result(Future.traverse(nodes)(connectToAll), 1.minute)
  }

  private def connectToNetwork(node: DockerNode): Unit = {
    log.info(s"Trying to connect node $node to network ...")
    client.connectToNetwork(
      tacNetwork.id(),
      NetworkConnection
        .builder()
        .containerId(node.containerId)
        .endpointConfig(endpointConfigFor(node.name))
        .build()
    )

    node.nodeInfo = getNodeInfo(node.containerId, node.settings)
    log.debug(s"New ${node.name} settings: ${node.nodeInfo}")
  }

  private def endpointConfigFor(nodeName: String): EndpointConfig = {
    val nodeNumber = nodeName.replace("node", "").toInt
    val ip         = ipForNode(nodeNumber)

    EndpointConfig
      .builder()
      .ipAddress(ip)
      .ipamConfig(EndpointIpamConfig.builder().ipv4Address(ip).build())
      .build()
  }

  private def dumpContainers(containers: java.util.List[Container], label: String = "Containers"): Unit = {
    val x =
      if (containers.isEmpty) "No"
      else
        "\n" + containers.asScala
          .map { x =>
            s"Container(${x.id()}, status: ${x.status()}, names: ${x.names().asScala.mkString(", ")})"
          }
          .mkString("\n")

    log.debug(s"$label: $x")
  }

}

object Docker {
  val NodeImageName: String = "com.tacplatform/node-it:latest"

  private val ContainerRoot = Paths.get("/usr/share/tac")
  private val ProfilerPort  = 10001

  private val RunId = Option(System.getenv("RUN_ID")).getOrElse(DateTimeFormatter.ofPattern("MM-dd--HH_mm_ss").format(LocalDateTime.now()))

  private val jsonMapper  = new ObjectMapper
  private val propsMapper = new JavaPropsMapper

  val configTemplate: Config = parseResources("template.conf")
  def genesisOverride: Config = {
    val genesisTs = System.currentTimeMillis()

    val timestampOverrides = parseString(s"""tac.blockchain.custom.genesis {
                                            |  timestamp = $genesisTs
                                            |  block-timestamp = $genesisTs
                                            |  signature = null # To calculate it in Block.genesis
                                            |}""".stripMargin)

    val genesisConfig    = timestampOverrides.withFallback(configTemplate)
    val gs               = genesisConfig.as[GenesisSettings]("tac.blockchain.custom.genesis")
    val genesisSignature = Block.genesis(gs).explicitGet().id()

    parseString(s"tac.blockchain.custom.genesis.signature = $genesisSignature").withFallback(timestampOverrides)
  }

  AddressScheme.current = new AddressScheme {
    override val chainId: Byte = configTemplate.as[String]("tac.blockchain.custom.address-scheme-character").charAt(0).toByte
  }

  def apply(owner: Class[_]): Docker = new Docker(tag = owner.getSimpleName)

  private def asProperties(config: Config): Properties = {
    val jsonConfig = config.resolve().root().render(ConfigRenderOptions.concise())
    propsMapper.writeValueAsProperties(jsonMapper.readTree(jsonConfig))
  }

  private def renderProperties(p: Properties) =
    p.asScala
      .map {
        case (k, v) if v.contains(" ") => k -> s""""$v""""
        case x                         => x
      }
      .map { case (k, v) => s"-D$k=$v" }
      .mkString(" ")

  case class NodeInfo(restApiPort: Int, networkPort: Int, tacIpAddress: String, ports: JMap[String, JList[PortBinding]]) {
    val nodeApiEndpoint: URL                       = new URL(s"http://localhost:${externalPort(restApiPort)}")
    val hostNetworkAddress: InetSocketAddress      = new InetSocketAddress("localhost", externalPort(networkPort))
    val containerNetworkAddress: InetSocketAddress = new InetSocketAddress(tacIpAddress, networkPort)

    def externalPort(internalPort: Int): Int = ports.get(s"$internalPort/tcp").get(0).hostPort().toInt
  }

  class DockerNode(config: Config, val containerId: String, private[Docker] var nodeInfo: NodeInfo) extends Node(config) {
    override def nodeExternalPort(internalPort: Int): Int = nodeInfo.externalPort(internalPort)

    override def nodeApiEndpoint: URL = nodeInfo.nodeApiEndpoint

    override val apiKey = "integration-test-rest-api"

    override def networkAddress: InetSocketAddress = nodeInfo.hostNetworkAddress

    def containerNetworkAddress: InetSocketAddress = nodeInfo.containerNetworkAddress

    def getConfig: Config = config
  }

}