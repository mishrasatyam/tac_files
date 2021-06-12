package com.tacplatform.transaction.smart

import com.tacplatform.account.{KeyPair, PublicKey}
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithDomain
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.history.Domain._
import com.tacplatform.it.util._
import com.tacplatform.lang.script.Script
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.v1.compiler.Terms
import com.tacplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.tacplatform.state.diffs.produce
import com.tacplatform.transaction.Asset
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.assets.exchange._
import com.tacplatform.transaction.assets.{IssueTransaction, SetAssetScriptTransaction}
import com.tacplatform.transaction.smart.script.ScriptCompiler
import com.tacplatform.{NTPTime, NoShrink, TransactionGen}
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class VerifierSpecification
    extends PropSpec
    with PropertyChecks
    with Matchers
    with MockFactory
    with TransactionGen
    with NTPTime
    with WithDomain
    with NoShrink {
  private def mkIssue(issuer: KeyPair, name: String, script: Option[Script] = None) =
    IssueTransaction
      .selfSigned(
        2.toByte,
        issuer,
        name,
        "",
        100000_00,
        2,
        reissuable = false,
        script,
        1.tac,
        ntpTime.getTimestamp()
      )
      .explicitGet()

  private def mkOrder(
      sender: KeyPair,
      orderType: OrderType,
      matcher: PublicKey,
      assetPair: AssetPair,
      fee: Long = 0.003.tac,
      feeAsset: Asset = Tac
  ) = Order.selfSigned(
    3.toByte,
    sender,
    matcher,
    assetPair,
    orderType,
    100,
    5.tac,
    ntpTime.getTimestamp(),
    ntpTime.getTimestamp() + 200000,
    fee,
    feeAsset
  )

  private val sharedParamGen = for {
    sender  <- accountGen
    matcher <- accountGen
    g1      <- genesisGeneratorP(sender.toAddress)
    g2      <- genesisGeneratorP(matcher.toAddress)
    issuedAsset = mkIssue(sender, "a001")
  } yield (
    sender,
    matcher,
    Seq(g1, g2, issuedAsset),
    AssetPair(IssuedAsset(issuedAsset.id()), Tac)
  )

  property("blockchain functions are available for order branch when verifying exchange transaction") {
    forAll(sharedParamGen) {
      case (sender, matcher, genesisTxs, assetPair) =>
        withDomain(
          domainSettingsWithPreactivatedFeatures(
            BlockchainFeatures.SmartAccountTrading,
            BlockchainFeatures.SmartAssets,
            BlockchainFeatures.OrderV3,
            BlockchainFeatures.Ride4DApps
          )
        ) { d =>
          d.appendBlock(genesisTxs: _*)
          d.appendBlock(
            SetScriptTransaction
              .selfSigned(
                1.toByte,
                sender,
                Some(
                  ScriptCompiler(
                    """match tx {
                    |  case _: Order => height >= 0
                    |  case _ => true
                    |}""".stripMargin,
                    isAssetScript = false,
                    ScriptEstimatorV2
                  ).explicitGet()._1
                ),
                0.001.tac,
                ntpTime.getTimestamp()
              )
              .explicitGet()
          )

          d.appendBlock(
            ExchangeTransaction
              .signed(
                2.toByte,
                matcher.privateKey,
                mkOrder(sender, OrderType.BUY, matcher.publicKey, assetPair),
                mkOrder(sender, OrderType.SELL, matcher.publicKey, assetPair),
                100,
                5.tac,
                0.003.tac,
                0.003.tac,
                0.003.tac,
                ntpTime.getTimestamp()
              )
              .explicitGet()
          )
        }
    }
  }

  private val sharedParamGen2 = for {
    (sender, matcher, genesisTxs, assetPair) <- sharedParamGen
    buyFeeAssetTx  = mkIssue(sender, "BUYFEE", Some(ExprScript(Terms.TRUE).explicitGet()))
    buyFeeAssetId  = IssuedAsset(buyFeeAssetTx.id())
    sellFeeAssetTx = mkIssue(sender, "SELLFEE", Some(ExprScript(Terms.TRUE).explicitGet()))
    sellFeeAssetId = IssuedAsset(sellFeeAssetTx.id())
  } yield (
    sender,
    genesisTxs ++ Seq(buyFeeAssetTx, sellFeeAssetTx),
    ExchangeTransaction
      .signed(
        2.toByte,
        matcher.privateKey,
        mkOrder(sender, OrderType.BUY, matcher.publicKey, assetPair, 100, buyFeeAssetId),
        mkOrder(sender, OrderType.SELL, matcher.publicKey, assetPair, 100, sellFeeAssetId),
        100,
        5.tac,
        100,
        100,
        0.003.tac,
        ntpTime.getTimestamp()
      )
      .explicitGet(),
    buyFeeAssetId,
    sellFeeAssetId
  )

  property("matcher fee asset script is executed during exchange transaction validation") {
    forAll(sharedParamGen2) {
      case (sender, genesisTxs, exchangeTx, buyFeeAsset, sellFeeAsset) =>
        def setAssetScript(assetId: IssuedAsset, script: Option[Script]): SetAssetScriptTransaction =
          SetAssetScriptTransaction.selfSigned(1.toByte, sender, assetId, script, 0.001.tac, ntpTime.getTimestamp()).explicitGet()

        withDomain(
          domainSettingsWithPreactivatedFeatures(
            BlockchainFeatures.SmartAccountTrading,
            BlockchainFeatures.OrderV3,
            BlockchainFeatures.SmartAssets,
            BlockchainFeatures.Ride4DApps
          )
        ) { d =>
          d.appendBlock(genesisTxs: _*)

          d.blockchainUpdater.processBlock(
            d.createBlock(2.toByte, Seq(setAssetScript(buyFeeAsset, Some(ExprScript(Terms.FALSE).explicitGet())), exchangeTx))
          ) should produce("TransactionNotAllowedByScript")

          d.blockchainUpdater.processBlock(
            d.createBlock(
              2.toByte,
              Seq(setAssetScript(sellFeeAsset, Some(ScriptCompiler.compile("(5 / 0) == 2", ScriptEstimatorV2).explicitGet()._1)), exchangeTx)
            )
          ) should produce("ScriptExecutionError(error = / by zero")
        }
    }
  }
}
