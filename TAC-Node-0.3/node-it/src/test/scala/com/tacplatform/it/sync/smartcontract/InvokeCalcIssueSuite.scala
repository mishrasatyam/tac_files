package com.tacplatform.it.sync.smartcontract

import com.typesafe.config.Config
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.it.NodeConfigs
import com.tacplatform.it.NodeConfigs.Default
import com.tacplatform.it.api.SyncHttpApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.transactions.BaseTransactionSuite
import com.tacplatform.it.util._
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.state.BinaryDataEntry
import com.tacplatform.transaction.smart.script.ScriptCompiler
import org.scalatest.{CancelAfterFailure, Matchers, OptionValues}

class InvokeCalcIssueSuite extends BaseTransactionSuite with Matchers with CancelAfterFailure with OptionValues {
  import InvokeCalcIssueSuite._

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs
      .Builder(Default, 1, Seq.empty)
      .overrideBase(_.quorum(0))
      .overrideBase(_.preactivatedFeatures((BlockchainFeatures.BlockV5.id, 0), (BlockchainFeatures.BlockV5.id, 0)))
      .buildNonConflicting()

  private def smartAcc  = firstKeyPair
  private def callerAcc = secondKeyPair

  test("calculateAssetId should return right unique id for each invoke") {

    sender.setScript(
      smartAcc,
      Some(ScriptCompiler.compile(dAppV4, ScriptEstimatorV3).explicitGet()._1.bytes().base64),
      fee = setScriptFee + smartFee,
      waitForTx = true
    )
    val smartAccAddress = smartAcc.toAddress.toString
    sender
      .invokeScript(
        callerAcc,
        smartAccAddress,
        Some("i"),
        args = List.empty,
        fee = invokeFee + issueFee, // dAppV4 contains 1 Issue action
        waitForTx = true
      )
    val assetId = sender.getDataByKey(smartAccAddress, "id").as[BinaryDataEntry].value.toString

    sender
      .invokeScript(
        callerAcc,
        smartAccAddress,
        Some("i"),
        args = List.empty,
        fee = invokeFee + issueFee, // dAppV4 contains 1 Issue action
        waitForTx = true
      )
    val secondAssetId = sender.getDataByKey(smartAccAddress, "id").as[BinaryDataEntry].value.toString

    sender.assetBalance(smartAccAddress, assetId).balance shouldBe 100
    sender.assetBalance(smartAccAddress, secondAssetId).balance shouldBe 100

    val assetDetails = sender.assetsDetails(assetId)
    assetDetails.decimals shouldBe decimals
    assetDetails.name shouldBe assetName
    assetDetails.reissuable shouldBe reissuable
    assetDetails.description shouldBe assetDescr
    assetDetails.minSponsoredAssetFee shouldBe None

  }
}

object InvokeCalcIssueSuite {

  val assetName  = "InvokeAsset"
  val assetDescr = "Invoke asset descr"
  val amount     = 100
  val decimals   = 0
  val reissuable = true

  private val dAppV4: String =
    s"""{-# STDLIB_VERSION 4 #-}
      |{-# CONTENT_TYPE DAPP #-}
      |
      |@Callable(i)
      |func i() = {
      |let issue = Issue("$assetName", "$assetDescr", $amount, $decimals, $reissuable, unit, 0)
      |let id = calculateAssetId(issue)
      |[issue,
      | BinaryEntry("id", id)]
      |}
      |
      |""".stripMargin
}
