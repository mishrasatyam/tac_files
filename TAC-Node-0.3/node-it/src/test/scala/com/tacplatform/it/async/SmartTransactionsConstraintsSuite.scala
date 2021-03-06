package com.tacplatform.it.async

import com.typesafe.config.Config
import com.tacplatform.account.KeyPair
import com.tacplatform.api.http.requests.SignedSetScriptRequest
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.AsyncHttpApi._
import com.tacplatform.it.transactions.NodesFromDocker
import com.tacplatform.it.{NodeConfigs, TransferSending}
import com.tacplatform.lang.directives.values.V1
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.v1.compiler.Terms
import com.tacplatform.mining.MiningConstraints.MaxScriptRunsInBlock
import com.tacplatform.transaction.TxVersion
import com.tacplatform.transaction.smart.SetScriptTransaction
import org.scalatest._
import play.api.libs.json.{JsNumber, Json}

import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SmartTransactionsConstraintsSuite extends FreeSpec with Matchers with TransferSending with NodesFromDocker {

  override protected val nodeConfigs: Seq[Config] = NodeConfigs.newBuilder
    .overrideBase(
      _.raw(
        s"""akka.http.server {
         |  parsing.max-content-length = 3737439
         |  request-timeout = 60s
         |}
         |
         |tac {
         |  miner.quorum = 0
         |
         |  blockchain.custom {
         |    functionality {
         |      pre-activated-features {
         |        2: 0
         |        4: 0
         |        11: 100500
         |      }
         |    }
         |  }
         |}""".stripMargin
      )
    )
    .withDefault(1)
    .build(false)

  private def miner                   = nodes.head
  private val smartPrivateKey  = KeyPair.fromSeed(NodeConfigs.Default(1).getString("account-seed")).explicitGet()
  private val simplePrivateKey = KeyPair.fromSeed(NodeConfigs.Default(2).getString("account-seed")).explicitGet()

  s"Block is limited by size after activation" in result(
    for {
      _ <- miner.signedBroadcast(Json.toJsObject(toRequest(setScriptTx(smartPrivateKey))) + ("type" -> JsNumber(13)))
      _ <- processRequests(generateTransfersFromAccount(MaxScriptRunsInBlock * 3, smartPrivateKey.toAddress.toString))
      _ <- miner.waitForHeight(5)
      _ <- processRequests(generateTransfersFromAccount(MaxScriptRunsInBlock * 3, smartPrivateKey.toAddress.toString))
      _ <- scala.concurrent.Future.sequence((0 to 9).map(_ =>
        processRequests(generateTransfersFromAccount((50 - MaxScriptRunsInBlock / 10), simplePrivateKey.toAddress.toString))))
      _                  <- miner.waitForHeight(6)
      blockWithSetScript <- miner.blockHeadersAt(2)
      restBlocks         <- miner.blockHeadersSeq(3, 4)
      newBlock           <- miner.blockHeadersAt(5)
    } yield {
      blockWithSetScript.transactionCount should (be <= (MaxScriptRunsInBlock + 1) and be >= 1)
      restBlocks.foreach { x =>
        x.transactionCount should be(MaxScriptRunsInBlock)
      }
      newBlock.transactionCount should be > MaxScriptRunsInBlock
    },
    12.minutes
  )

  private def setScriptTx(sender: KeyPair) =
    SetScriptTransaction
      .selfSigned(1.toByte, sender = sender, script = Some(ExprScript(V1, Terms.TRUE, checkSize = false).explicitGet()), fee = 1000000, timestamp = System.currentTimeMillis() - 5.minutes.toMillis)
      .explicitGet()

  private def toRequest(tx: SetScriptTransaction): SignedSetScriptRequest = SignedSetScriptRequest(
    version = Some(TxVersion.V1),
    senderPublicKey = tx.sender.toString,
    script = tx.script.map(_.bytes().base64),
    fee = tx.fee,
    timestamp = tx.timestamp,
    proofs = tx.proofs
  )

}
