package com.tacplatform.state.diffs.smart
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithDomain
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.history.Domain
import com.tacplatform.lang.directives.values.V4
import com.tacplatform.lang.v1.compiler.TestCompiler
import com.tacplatform.settings.{Constants, TestFunctionalitySettings}
import com.tacplatform.state.EmptyDataEntry
import com.tacplatform.state.diffs.ENOUGH_AMT
import com.tacplatform.state.diffs.FeeValidation.{FeeConstants, FeeUnit}
import com.tacplatform.state.diffs.ci.ciFee
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{DataTransaction, GenesisTransaction, Transaction, TxWithFee}
import com.tacplatform.{NoShrink, TestTime, TransactionGen}
import org.scalatest.{EitherValues, Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class SmartAccountFeeTest
    extends PropSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with TransactionGen
    with NoShrink
    with WithDomain
    with EitherValues {

  private val time = new TestTime
  private def ts   = time.getTimestamp()

  private val activationHeight = 3

  private val scriptWithEmptyVerifier = TestCompiler(V4).compileContract("""
    | {-# STDLIB_VERSION 4       #-}
    | {-# CONTENT_TYPE   DAPP    #-}
    | {-# SCRIPT_TYPE    ACCOUNT #-}
    |
    |""".stripMargin)

  private val scriptWithSmallVerifier = TestCompiler(V4).compileContract("""
    | {-# STDLIB_VERSION 4       #-}
    | {-# CONTENT_TYPE   DAPP    #-}
    | {-# SCRIPT_TYPE    ACCOUNT #-}
    |
    | @Verifier(tx)
    | func verify() =
    |   sigVerify_16Kb(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey) &&
    |   sigVerify_16Kb(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey) &&
    |   sigVerify_8Kb(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey)
    |
    | @Callable(i)
    | func default() = []
    |""".stripMargin)

  private val scriptWithPaidVerifier = TestCompiler(V4).compileContract("""
    | {-# STDLIB_VERSION 4       #-}
    | {-# CONTENT_TYPE   DAPP    #-}
    | {-# SCRIPT_TYPE    ACCOUNT #-}
    |
    | @Verifier(tx)
    | func verify() =
    |   sigVerify_16Kb(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey) &&
    |   sigVerify_16Kb(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey) &&
    |   sigVerify_16Kb(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey)
    |
    | @Callable(i)
    | func default() = []
    |""".stripMargin)

  private val features = TestFunctionalitySettings.Enabled.copy(
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
    featureCheckBlocksPeriod = 1,
    blocksForFeatureActivation = 1
  )

  private val preconditions =
    for {
      accountWithPaidVerifier  <- accountGen
      accountWithSmallVerifier <- accountGen
      accountWithEmptyVerifier <- accountGen
      transferFee  = FeeUnit * FeeConstants(TransferTransaction.typeId)
      setScriptFee = FeeUnit * FeeConstants(SetScriptTransaction.typeId)
      invokeFee <- ciFee()
    } yield {
      for {
        genesis    <- GenesisTransaction.create(accountWithPaidVerifier.toAddress, ENOUGH_AMT, ts)
        genesis2   <- GenesisTransaction.create(accountWithSmallVerifier.toAddress, ENOUGH_AMT, ts)
        genesis3   <- GenesisTransaction.create(accountWithEmptyVerifier.toAddress, ENOUGH_AMT, ts)
        setScript  <- SetScriptTransaction.selfSigned(1.toByte, accountWithPaidVerifier, Some(scriptWithPaidVerifier), setScriptFee, ts)
        setScript2 <- SetScriptTransaction.selfSigned(1.toByte, accountWithSmallVerifier, Some(scriptWithSmallVerifier), setScriptFee, ts)
        setScript3 <- SetScriptTransaction.selfSigned(1.toByte, accountWithEmptyVerifier, Some(scriptWithEmptyVerifier), setScriptFee, ts)
        invokeFromPaidVerifier = () =>
          InvokeScriptTransaction
            .selfSigned(
              1.toByte,
              accountWithPaidVerifier,
              accountWithSmallVerifier.toAddress,
              None,
              Nil,
              invokeFee,
              Tac,
              ts
            )
            .explicitGet()
        invokeFromSmallVerifier = () =>
          InvokeScriptTransaction
            .selfSigned(
              1.toByte,
              accountWithSmallVerifier,
              accountWithPaidVerifier.toAddress,
              None,
              Nil,
              invokeFee,
              Tac,
              ts
            )
            .explicitGet()
        invokeFromEmptyVerifier = () =>
          InvokeScriptTransaction
            .selfSigned(
              1.toByte,
              accountWithEmptyVerifier,
              accountWithPaidVerifier.toAddress,
              None,
              Nil,
              invokeFee,
              Tac,
              ts
            )
            .explicitGet()
        transferFromSmallVerifier = () =>
          TransferTransaction
            .selfSigned(
              2.toByte,
              accountWithSmallVerifier,
              accountWithPaidVerifier.toAddress,
              Tac,
              1,
              Tac,
              transferFee,
              ByteStr.empty,
              ts
            )
            .explicitGet()
        transferFromPaidVerifier = () =>
          TransferTransaction
            .selfSigned(
              2.toByte,
              accountWithPaidVerifier,
              accountWithSmallVerifier.toAddress,
              Tac,
              1,
              Tac,
              transferFee,
              ByteStr.empty,
              ts
            )
            .explicitGet()
        transferFromEmptyVerifier = () =>
          TransferTransaction
            .selfSigned(
              2.toByte,
              accountWithEmptyVerifier,
              accountWithSmallVerifier.toAddress,
              Tac,
              1,
              Tac,
              transferFee,
              ByteStr.empty,
              ts
            )
            .explicitGet()
        dataFromSmallVerifier = () =>
          DataTransaction
            .selfSigned(
              2.toByte,
              accountWithSmallVerifier,
              Seq(EmptyDataEntry("key")),
              transferFee,
              ts
            )
            .explicitGet()
        dataFromPaidVerifier = () =>
          DataTransaction
            .selfSigned(
              2.toByte,
              accountWithPaidVerifier,
              Seq(EmptyDataEntry("key")),
              transferFee,
              ts
            )
            .explicitGet()
        dataFromEmptyVerifier = () =>
          DataTransaction
            .selfSigned(
              2.toByte,
              accountWithEmptyVerifier,
              Seq(EmptyDataEntry("key")),
              transferFee,
              ts
            )
            .explicitGet()
      } yield (
        List(genesis, genesis2, genesis3, setScript, setScript2, setScript3),
        List(invokeFromPaidVerifier, transferFromPaidVerifier, dataFromPaidVerifier),
        List(
          invokeFromSmallVerifier,
          transferFromSmallVerifier,
          dataFromSmallVerifier,
          invokeFromEmptyVerifier,
          transferFromEmptyVerifier,
          dataFromEmptyVerifier
        )
      )
    }.explicitGet()

  private def appendAndAssertNotEnoughFee(tx: Transaction with TxWithFee, d: Domain) = {
    val e = the[RuntimeException] thrownBy d.appendBlock(tx)
    e.getMessage should startWith
    "TransactionValidationError(cause = GenericError(Transaction sent from smart account. " +
      s"Requires $ScriptExtraFee extra fee.. " +
      s"Fee for ${Constants.TransactionNames(tx.typeId)} (${tx.fee} in TAC) " +
      s"does not exceed minimal value of ${FeeConstants(tx.typeId) * FeeUnit + ScriptExtraFee} TAC.)"
  }

  private def assertNoError(tx: Transaction, d: Domain) =
    d.blockchain.bestLiquidDiff.get.errorMessage(tx.id.value()) shouldBe None

  property(s"small verifier is free after ${BlockchainFeatures.SynchronousCalls} activation") {
    forAll(preconditions) {
      case (preparingTxs, paidVerifierTxs, freeVerifierTxs) =>
        withDomain(domainSettingsWithFS(features)) { d =>
          d.appendBlock(preparingTxs: _*)

          (paidVerifierTxs ::: freeVerifierTxs).foreach(tx => appendAndAssertNotEnoughFee(tx(), d))

          d.appendBlock()
          d.appendBlock()
          d.blockchain.height shouldBe activationHeight
          d.blockchain.bestLiquidDiff.get.scriptsRun shouldBe 0

          paidVerifierTxs.foreach(tx => appendAndAssertNotEnoughFee(tx(), d))
          d.appendBlock(freeVerifierTxs.map(_()): _*)
          freeVerifierTxs.foreach(tx => assertNoError(tx(), d))
          d.blockchain.bestLiquidDiff.get.scriptsRun shouldBe freeVerifierTxs.size
        }
    }
  }
}
