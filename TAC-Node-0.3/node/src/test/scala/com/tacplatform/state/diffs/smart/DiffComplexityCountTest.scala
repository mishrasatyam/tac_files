package com.tacplatform.state.diffs.smart

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
import com.tacplatform.state.diffs.ci.ciFee
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.GenesisTransaction
import com.tacplatform.transaction.assets.IssueTransaction
import com.tacplatform.transaction.smart.InvokeScriptTransaction.Payment
import com.tacplatform.transaction.smart.script.ScriptCompiler
import com.tacplatform.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.{NoShrink, TestTime, TransactionGen}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{EitherValues, Inside, Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class DiffComplexityCountTest
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

  // ~1900 complexity
  val groth: String =
    s"""
       | let key = base64'mY//hEITCBCZUJUN/wsOlw1iUSSOESL6PFSbN1abGK80t5jPNICNlPuSorio4mmWpf+4uOyv3gPZe54SYGM4pfhteqJpwFQxdlpwXWyYxMTNaSLDj8VtSn/EJaSu+P6nFmWsda3mTYUPYMZzWE4hMqpDgFPcJhw3prArMThDPbR3Hx7E6NRAAR0LqcrdtsbDqu2T0tto1rpnFILdvHL4PqEUfTmF2mkM+DKj7lKwvvZUbukqBwLrnnbdfyqZJryzGAMIa2JvMEMYszGsYyiPXZvYx6Luk54oWOlOrwEKrCY4NMPwch6DbFq6KpnNSQwOpgRYCz7wpjk57X+NGJmo85tYKc+TNa1rT4/DxG9v6SHkpXmmPeHhzIIW8MOdkFjxB5o6Qn8Fa0c6Tt6br2gzkrGr1eK5/+RiIgEzVhcRrqdY/p7PLmKXqawrEvIv9QZ3ijytPNwinlC8XdRLO/YvP33PjcI9WSMcHV6POP9KPMo1rngaIPMegKgAvTEouNFKp4v3wAXRXX5xEjwXAmM5wyB/SAOaPPCK/emls9kqolHsaj7nuTTbrvSV8bqzUwzQ'
       | let proof = base64'g53N8ecorvG2sDgNv8D7quVhKMIIpdP9Bqk/8gmV5cJ5Rhk9gKvb4F0ll8J/ZZJVqa27OyciJwx6lym6QpVK9q1ASrqio7rD5POMDGm64Iay/ixXXn+//F+uKgDXADj9AySri2J1j3qEkqqe3kxKthw94DzAfUBPncHfTPazVtE48AfzB1KWZA7Vf/x/3phYs4ckcP7ZrdVViJVLbUgFy543dpKfEH2MD30ZLLYRhw8SatRCyIJuTZcMlluEKG+d'
       | let input = base64'aZ8tqrOeEJKt4AMqiRF/WJhIKTDC0HeDTgiJVLZ8OEs='
       | groth16Verify_8inputs(key, proof, input)
    """.stripMargin

  private val verifier: Script = {
    val script = s"""
                    | {-# STDLIB_VERSION 4        #-}
                    | {-# SCRIPT_TYPE ASSET       #-}
                    | {-# CONTENT_TYPE EXPRESSION #-}
                    |
                    | if (true)
                    |   then true
                    |   else ($groth)
                    |
                  """.stripMargin
    ScriptCompiler.compile(script, ScriptEstimatorV3).explicitGet()._1
  }

  private def dApp(asset: IssuedAsset): Script = TestCompiler(V4).compileContract(
      s"""
         | {-# STDLIB_VERSION 4       #-}
         | {-# CONTENT_TYPE   DAPP    #-}
         | {-# SCRIPT_TYPE    ACCOUNT #-}
         |
         | @Callable(i)
         | func default() = {
         |   strict cond =
         |     if (true)
         |       then true
         |       else ($groth)
         |
         |   [
         |     ScriptTransfer(i.caller, 1, base58'$asset'),
         |     Burn(base58'$asset', 1),
         |     Reissue(base58'$asset', 1, true)
         |   ]
         | }
       """.stripMargin
  )

  private val paymentPreconditions =
    for {
      account1 <- accountGen
      account2 <- accountGen
      fee      <- ciFee(sc = 6)
    } yield {
      for {
        genesis  <- GenesisTransaction.create(account1.toAddress, ENOUGH_AMT, ts)
        genesis2 <- GenesisTransaction.create(account2.toAddress, ENOUGH_AMT, ts)
        issue    <- IssueTransaction.selfSigned(2.toByte, account1, "Asset", "Description", ENOUGH_AMT, 8, true, Some(verifier), fee, ts)
        asset = IssuedAsset(issue.id.value())
        transfer1   <- TransferTransaction.selfSigned(2.toByte, account1, account2.toAddress, asset, Int.MaxValue, Tac, fee, ByteStr.empty, ts)
        setVerifier <- SetScriptTransaction.selfSigned(1.toByte, account2, Some(verifier), fee, ts)
        setDApp     <- SetScriptTransaction.selfSigned(1.toByte, account1, Some(dApp(asset)), fee, ts)
        payments = Seq(Payment(1, asset), Payment(1, asset))
        invokeFromScripted = () =>
          InvokeScriptTransaction.selfSigned(1.toByte, account2, account1.toAddress, None, payments, fee, Tac, ts).explicitGet()
      } yield (List(genesis, genesis2, issue, transfer1, setVerifier, setDApp), invokeFromScripted)
    }.explicitGet()

  property(s"evaluated complexity is used for diff instead of estimated one after activation ${BlockchainFeatures.SynchronousCalls}") {
    val (preparingTxs, invoke) = paymentPreconditions.sample.get
    withDomain(domainSettingsWithFS(fsWithV5)) { d =>
      d.appendBlock(preparingTxs: _*)

      val invoke1 = invoke()
      d.appendBlock(invoke1)
      d.blockchain.bestLiquidDiff.get.errorMessage(invoke1.id.value()) shouldBe empty
      d.blockchain.bestLiquidDiff.get.scriptsComplexity shouldBe 13382  // dApp + 3 actions + 2 payments + verifier = 7 * 1900 = 13300

      d.appendBlock()
      d.blockchainUpdater.height shouldBe activationHeight

      val invoke2 = invoke()
      d.appendBlock(invoke2)
      d.blockchain.bestLiquidDiff.get.errorMessage(invoke2.id.value()) shouldBe empty
      d.blockchain.bestLiquidDiff.get.scriptsComplexity shouldBe 17
    }
  }
}
