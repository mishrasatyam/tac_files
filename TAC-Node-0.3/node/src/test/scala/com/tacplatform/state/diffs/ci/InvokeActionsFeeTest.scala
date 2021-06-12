package com.tacplatform.state.diffs.ci

import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.{DBCacheSettings, WithDomain, WithState}
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.lang.directives.values.V4
import com.tacplatform.lang.script.Script
import com.tacplatform.lang.v1.compiler.TestCompiler
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.settings.TestFunctionalitySettings
import com.tacplatform.state.diffs.ENOUGH_AMT
import com.tacplatform.state.diffs.FeeValidation.{FeeConstants, FeeUnit}
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.assets.IssueTransaction
import com.tacplatform.transaction.smart.InvokeScriptTransaction.Payment
import com.tacplatform.transaction.smart.script.ScriptCompiler
import com.tacplatform.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{GenesisTransaction, Transaction}
import com.tacplatform.{NoShrink, TestTime, TransactionGen}
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.{EitherValues, Inside, Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class InvokeActionsFeeTest
    extends PropSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with TransactionGen
    with NoShrink
    with Inside
    with WithState
    with DBCacheSettings
    with MockFactory
    with WithDomain
    with EitherValues {

  private val time = new TestTime
  private def ts   = time.getTimestamp()

  private val activationHeight = 3

  private val fsWithV5 = TestFunctionalitySettings.Enabled.copy(
    preActivatedFeatures = Map(
      BlockchainFeatures.SmartAccounts.id    -> 0,
      BlockchainFeatures.SmartAssets.id      -> 0,
      BlockchainFeatures.Ride4DApps.id       -> 0,
      BlockchainFeatures.FeeSponsorship.id   -> 0,
      BlockchainFeatures.DataTransaction.id  -> 0,
      BlockchainFeatures.BlockReward.id      -> 0,
      BlockchainFeatures.BlockV5.id          -> 0,
      BlockchainFeatures.SynchronousCalls.id -> activationHeight
    ),
    estimatorPreCheckHeight = Int.MaxValue
  )

  val verifier: Script = {
    val script = s"""
                    | {-# STDLIB_VERSION 4        #-}
                    | {-# SCRIPT_TYPE ASSET       #-}
                    | {-# CONTENT_TYPE EXPRESSION #-}
                    |
                    | !(sigVerify_32Kb(base58'', base58'', base58'') ||
                    |   sigVerify_32Kb(base58'', base58'', base58'') ||
                    |   sigVerify_32Kb(base58'', base58'', base58'')
                    |  )
                    |
                    """.stripMargin
    ScriptCompiler.compile(script, ScriptEstimatorV3).explicitGet()._1
  }

  private def dApp(asset: IssuedAsset): Script =
    TestCompiler(V4).compileContract(s"""
         | {-# STDLIB_VERSION 4       #-}
         | {-# CONTENT_TYPE   DAPP    #-}
         | {-# SCRIPT_TYPE    ACCOUNT #-}
         |
         | @Callable(i)
         | func default() =
         |  [
         |     ScriptTransfer(i.caller, 1, base58'$asset'),
         |     Burn(base58'$asset', 1),
         |     Reissue(base58'$asset', 1, false)
         |  ]
      """.stripMargin)

  private val paymentPreconditions: Gen[(List[Transaction], () => InvokeScriptTransaction, () => InvokeScriptTransaction)] =
    for {
      dAppAcc            <- accountGen
      scriptedInvoker    <- accountGen
      nonScriptedInvoker <- accountGen
      fee                <- ciFee()
    } yield {
      for {
        genesis  <- GenesisTransaction.create(dAppAcc.toAddress, ENOUGH_AMT, ts)
        genesis2 <- GenesisTransaction.create(scriptedInvoker.toAddress, ENOUGH_AMT, ts)
        genesis3 <- GenesisTransaction.create(nonScriptedInvoker.toAddress, ENOUGH_AMT, ts)
        issue    <- IssueTransaction.selfSigned(2.toByte, dAppAcc, "Asset", "Description", ENOUGH_AMT, 8, true, Some(verifier), fee, ts)
        asset = IssuedAsset(issue.id.value())
        transfer1 <- TransferTransaction.selfSigned(2.toByte, dAppAcc, scriptedInvoker.toAddress, asset, Int.MaxValue, Tac, fee, ByteStr.empty, ts)
        transfer2 <- TransferTransaction.selfSigned(
          2.toByte,
          dAppAcc,
          nonScriptedInvoker.toAddress,
          asset,
          Int.MaxValue,
          Tac,
          fee,
          ByteStr.empty,
          ts
        )
        setVerifier <- SetScriptTransaction.selfSigned(1.toByte, scriptedInvoker, Some(verifier), fee, ts)
        setDApp     <- SetScriptTransaction.selfSigned(1.toByte, dAppAcc, Some(dApp(asset)), fee, ts)
        payments = Seq(Payment(1, asset), Payment(1, asset))
        invokeFromScripted = () =>
          InvokeScriptTransaction.selfSigned(1.toByte, scriptedInvoker, dAppAcc.toAddress, None, payments, fee, Tac, ts).explicitGet()
        invokeFromNonScripted = () =>
          InvokeScriptTransaction.selfSigned(1.toByte, nonScriptedInvoker, dAppAcc.toAddress, None, payments, fee, Tac, ts).explicitGet()
      } yield (List(genesis, genesis2, genesis3, issue, transfer1, transfer2, setVerifier, setDApp), invokeFromScripted, invokeFromNonScripted)
    }.explicitGet()

  property(s"fee for asset scripts is not required after activation ${BlockchainFeatures.SynchronousCalls}") {
    val (preparingTxs, invokeFromScripted, invokeFromNonScripted) = paymentPreconditions.sample.get
    withDomain(domainSettingsWithFS(fsWithV5)) { d =>
      d.appendBlock(preparingTxs: _*)

      val invokeFromScripted1    = invokeFromScripted()
      val invokeFromNonScripted1 = invokeFromNonScripted()
      d.appendBlock(invokeFromScripted1, invokeFromNonScripted1)
      d.blockchain.bestLiquidDiff.get.errorMessage(invokeFromScripted1.id.value()).get.text should include(
        s"Fee in TAC for InvokeScriptTransaction (${invokeFromScripted1.fee} in TAC) " +
          s"with 6 total scripts invoked " +
          s"does not exceed minimal value of ${FeeConstants(InvokeScriptTransaction.typeId) * FeeUnit + 6 * ScriptExtraFee} TAC"
      )
      d.blockchain.bestLiquidDiff.get.errorMessage(invokeFromNonScripted1.id.value()).get.text should include(
        s"Fee in TAC for InvokeScriptTransaction (${invokeFromNonScripted1.fee} in TAC) " +
          s"with 5 total scripts invoked " +
          s"does not exceed minimal value of ${FeeConstants(InvokeScriptTransaction.typeId) * FeeUnit + 5 * ScriptExtraFee} TAC"
      )

      d.appendBlock()
      d.blockchainUpdater.height shouldBe activationHeight

      val invokeFromScripted2    = invokeFromScripted()
      val invokeFromNonScripted2 = invokeFromNonScripted()
      d.appendBlock(invokeFromScripted2, invokeFromNonScripted2)
      d.blockchain.bestLiquidDiff.get.errorMessage(invokeFromScripted2.id.value()).get.text should include(
        s"Fee in TAC for InvokeScriptTransaction (${invokeFromScripted2.fee} in TAC) " +
          s"with 1 total scripts invoked " +
          s"does not exceed minimal value of ${FeeConstants(InvokeScriptTransaction.typeId) * FeeUnit + ScriptExtraFee} TAC"
      )
      d.blockchain.bestLiquidDiff.get.errorMessage(invokeFromNonScripted2.id.value()) shouldBe None
    }
  }
}
