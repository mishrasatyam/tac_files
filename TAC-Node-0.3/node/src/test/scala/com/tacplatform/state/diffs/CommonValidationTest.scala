package com.tacplatform.state.diffs

import com.google.protobuf.ByteString
import com.tacplatform.account.{AddressScheme, Alias}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithState
import com.tacplatform.features.{BlockchainFeature, BlockchainFeatures}
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.lang.ValidationError
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.v1.compiler.Terms._
import com.tacplatform.mining.MiningConstraint
import com.tacplatform.settings.{Constants, FunctionalitySettings, TestFunctionalitySettings}
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.assets._
import com.tacplatform.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.tacplatform.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.tacplatform.transaction.transfer.MassTransferTransaction.ParsedTransfer
import com.tacplatform.transaction.transfer._
import com.tacplatform.transaction.{CreateAliasTransaction, DataTransaction, GenesisTransaction, PaymentTransaction, Proofs, Transaction, TxVersion}
import com.tacplatform.utils._
import com.tacplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class CommonValidationTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with WithState with NoShrink {

  property("disallows double spending") {
    val preconditionsAndPayment: Gen[(GenesisTransaction, TransferTransaction)] = for {
      master    <- accountGen
      recipient <- otherAccountGen(candidate = master)
      ts        <- positiveIntGen
      genesis: GenesisTransaction = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      transfer: TransferTransaction <- tacTransferGeneratorP(master, recipient.toAddress)
    } yield (genesis, transfer)

    forAll(preconditionsAndPayment) {
      case (genesis, transfer) =>
        assertDiffEi(Seq(TestBlock.create(Seq(genesis, transfer))), TestBlock.create(Seq(transfer))) { blockDiffEi =>
          blockDiffEi should produce("AlreadyInTheState")
        }

        assertDiffEi(Seq(TestBlock.create(Seq(genesis))), TestBlock.create(Seq(transfer, transfer))) { blockDiffEi =>
          blockDiffEi should produce("AlreadyInTheState")
        }
    }
  }

  private def sponsoredTransactionsCheckFeeTest(feeInAssets: Boolean, feeAmount: Long)(f: Either[ValidationError, Unit] => Any): Unit = {
    val settings = createSettings(BlockchainFeatures.FeeSponsorship -> 0)
    val gen      = sponsorAndSetScriptGen(sponsorship = true, smartToken = false, smartAccount = false, feeInAssets, feeAmount)
    forAll(gen) {
      case (genesisBlock, transferTx) =>
        withLevelDBWriter(settings) { blockchain =>
          val BlockDiffer.Result(preconditionDiff, preconditionFees, totalFee, _, _) =
            BlockDiffer.fromBlock(blockchain, None, genesisBlock, MiningConstraint.Unlimited).explicitGet()
          blockchain.append(preconditionDiff, preconditionFees, totalFee, None, genesisBlock.header.generationSignature, genesisBlock)

          f(FeeValidation(blockchain, transferTx))
        }
    }
  }

  property("checkFee for sponsored transactions sunny") {
    sponsoredTransactionsCheckFeeTest(feeInAssets = true, feeAmount = 10)(_.explicitGet())
  }

  property("checkFee for sponsored transactions fails if the fee is not enough") {
    sponsoredTransactionsCheckFeeTest(feeInAssets = true, feeAmount = 1)(_ should produce("does not exceed minimal value of"))
  }

  private def smartAccountCheckFeeTest(feeInAssets: Boolean, feeAmount: Long)(f: Either[ValidationError, Unit] => Any): Unit = {
    val settings = createSettings(BlockchainFeatures.SmartAccounts -> 0)
    val gen      = sponsorAndSetScriptGen(sponsorship = false, smartToken = false, smartAccount = true, feeInAssets, feeAmount)
    forAll(gen) {
      case (genesisBlock, transferTx) =>
        withLevelDBWriter(settings) { blockchain =>
          val BlockDiffer.Result(preconditionDiff, preconditionFees, totalFee, _, _) =
            BlockDiffer.fromBlock(blockchain, None, genesisBlock, MiningConstraint.Unlimited).explicitGet()
          blockchain.append(preconditionDiff, preconditionFees, totalFee, None, genesisBlock.header.generationSignature, genesisBlock)

          f(FeeValidation(blockchain, transferTx))
        }
    }
  }

  property("checkFee for smart accounts sunny") {
    smartAccountCheckFeeTest(feeInAssets = false, feeAmount = 400000)(_.explicitGet())
  }

  private def sponsorAndSetScriptGen(sponsorship: Boolean, smartToken: Boolean, smartAccount: Boolean, feeInAssets: Boolean, feeAmount: Long) =
    for {
      richAcc      <- accountGen
      recipientAcc <- accountGen
      ts = System.currentTimeMillis()
    } yield {
      val script = ExprScript(TRUE).explicitGet()

      val genesisTx = GenesisTransaction.create(richAcc.toAddress, ENOUGH_AMT, ts).explicitGet()

      val issueTx =
        if (smartToken)
          IssueTransaction(
            TxVersion.V2,
            richAcc.publicKey,
            "test".utf8Bytes,
            "desc".utf8Bytes,
            Long.MaxValue,
            2,
            reissuable = false,
            Some(script),
            Constants.UnitsInTac,
            ts
          ).signWith(richAcc.privateKey)
        else
          IssueTransaction(
            TxVersion.V1,
            richAcc.publicKey,
            "test".utf8Bytes,
            "desc".utf8Bytes,
            Long.MaxValue,
            2,
            reissuable = false,
            script = None,
            Constants.UnitsInTac,
            ts
          ).signWith(richAcc.privateKey)

      val transferTacTx = TransferTransaction.selfSigned(1.toByte, richAcc, recipientAcc.toAddress, Tac, 10 * Constants.UnitsInTac, Tac, 1 * Constants.UnitsInTac, ByteStr.empty, ts)
        .explicitGet()

      val transferAssetTx = TransferTransaction
        .selfSigned(
          1.toByte,
          richAcc,
          recipientAcc.toAddress,
          IssuedAsset(issueTx.id()),
          100,
          Tac,
          if (smartToken) {
            1 * Constants.UnitsInTac + ScriptExtraFee
          } else {
            1 * Constants.UnitsInTac
          }, ByteStr.empty,
          ts
        )
        .explicitGet()

      val sponsorTx =
        if (sponsorship)
          Seq(
            SponsorFeeTransaction
              .selfSigned(1.toByte, richAcc, IssuedAsset(issueTx.id()), Some(10), if (smartToken) {
                Constants.UnitsInTac + ScriptExtraFee
              } else {
                Constants.UnitsInTac
              }, ts)
              .explicitGet()
          )
        else Seq.empty

      val setScriptTx =
        if (smartAccount)
          Seq(
            SetScriptTransaction
              .selfSigned(1.toByte, recipientAcc, Some(script), 1 * Constants.UnitsInTac, ts)
              .explicitGet()
          )
        else Seq.empty

      val transferBackTx = TransferTransaction.selfSigned(
          1.toByte,
          recipientAcc,
          richAcc.toAddress,
          IssuedAsset(issueTx.id()),
          1,
          if (feeInAssets) IssuedAsset(issueTx.id()) else Tac,
          feeAmount, ByteStr.empty,
          ts
        )
        .explicitGet()

      (TestBlock.create(Vector[Transaction](genesisTx, issueTx, transferTacTx, transferAssetTx) ++ sponsorTx ++ setScriptTx), transferBackTx)
    }

  private def createSettings(preActivatedFeatures: (BlockchainFeature, Int)*): FunctionalitySettings =
    TestFunctionalitySettings.Enabled
      .copy(
        preActivatedFeatures = preActivatedFeatures.map { case (k, v) => k.id -> v }.toMap,
        blocksForFeatureActivation = 1,
        featureCheckBlocksPeriod = 1
      )

  private def smartTokensCheckFeeTest(feeInAssets: Boolean, feeAmount: Long)(f: Either[ValidationError, Unit] => Any): Unit = {
    val settings = createSettings(BlockchainFeatures.SmartAccounts -> 0, BlockchainFeatures.SmartAssets -> 0)
    val gen      = sponsorAndSetScriptGen(sponsorship = false, smartToken = true, smartAccount = false, feeInAssets, feeAmount)
    forAll(gen) {
      case (genesisBlock, transferTx) =>
        withLevelDBWriter(settings) { blockchain =>
          val BlockDiffer.Result(preconditionDiff, preconditionFees, totalFee, _, _) =
            BlockDiffer.fromBlock(blockchain, None, genesisBlock, MiningConstraint.Unlimited).explicitGet()
          blockchain.append(preconditionDiff, preconditionFees, totalFee, None, genesisBlock.header.generationSignature, genesisBlock)

          f(FeeValidation(blockchain, transferTx))
        }
    }
  }

  property("checkFee for smart tokens sunny") {
    smartTokensCheckFeeTest(feeInAssets = false, feeAmount = 1)(_.explicitGet())
  }

  property("disallows other network") {
    val preconditionsAndPayment: Gen[(GenesisTransaction, Transaction)] = for {
      master    <- accountGen
      recipient <- accountGen
      timestamp <- positiveLongGen
      amount    <- smallFeeGen
      script    <- scriptGen
      asset     <- bytes32gen.map(bs => IssuedAsset(ByteStr(bs)))
      genesis: GenesisTransaction = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, timestamp).explicitGet()

      invChainId <- invalidChainIdGen
      invChainAddr  = recipient.toAddress(invChainId)
      invChainAlias = Alias.createWithChainId("test", invChainId).explicitGet()
      invChainAddrOrAlias <- Gen.oneOf(invChainAddr, invChainAlias)

      tx <- Gen.oneOf(
        GenesisTransaction.create(invChainAddr, amount, timestamp).explicitGet(),
        PaymentTransaction.create(master, invChainAddr, amount, amount, timestamp).explicitGet(),
        TransferTransaction(
          TxVersion.V3,
          master.publicKey,
          invChainAddrOrAlias,
          Tac,
          amount,
          Tac,
          amount,
          ByteStr.empty,
          timestamp,
          Proofs.empty,
          invChainId
        ).signWith(master.privateKey),
        CreateAliasTransaction(TxVersion.V3, master.publicKey, invChainAlias.name, amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        LeaseTransaction(TxVersion.V3, master.publicKey, invChainAddrOrAlias, amount, amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        InvokeScriptTransaction(TxVersion.V2, master.publicKey, invChainAddrOrAlias, None, Nil, amount, Tac, timestamp, Proofs.empty, invChainId)
          .signWith(master.privateKey),
        exchangeV1GeneratorP(master, recipient, asset, Tac, None, invChainId).sample.get,
        IssueTransaction(
          TxVersion.V2,
          master.publicKey,
          ByteString.copyFrom(asset.id.arr),
          ByteString.copyFrom(asset.id.arr),
          amount,
          8: Byte,
          reissuable = true,
          None,
          amount,
          timestamp,
          Proofs.empty,
          invChainId
        ).signWith(master.privateKey),
        MassTransferTransaction(
          TxVersion.V2,
          master.publicKey,
          Tac,
          Seq(ParsedTransfer(invChainAddrOrAlias, amount)),
          amount,
          timestamp,
          ByteStr.empty,
          Proofs.empty,
          invChainId
        ).signWith(master.privateKey),
        LeaseCancelTransaction(TxVersion.V3, master.publicKey, asset.id, amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        SetScriptTransaction(TxVersion.V2, master.publicKey, Some(script), amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        SetAssetScriptTransaction(TxVersion.V2, master.publicKey, asset, Some(script), amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        BurnTransaction(TxVersion.V2, master.publicKey, asset, amount, amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        ReissueTransaction(TxVersion.V2, master.publicKey, asset, amount, reissuable = false, amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        SponsorFeeTransaction(TxVersion.V2, master.publicKey, asset, Some(amount), amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey),
        UpdateAssetInfoTransaction(TxVersion.V2, master.publicKey, asset, "1", "2", timestamp, amount, Tac, Proofs.empty, invChainId).signWith(master.privateKey),
        DataTransaction(TxVersion.V2, master.publicKey, Nil, amount, timestamp, Proofs.empty, invChainId).signWith(master.privateKey)
      )
    } yield (genesis, tx)

    forAll(preconditionsAndPayment) {
      case (genesis, tx) =>
        tx.chainId should not be AddressScheme.current.chainId
        assertDiffEi(Seq(TestBlock.create(Seq(genesis))), TestBlock.create(Seq(tx))) { blockDiffEi =>
          blockDiffEi should produce("Data from other network")
        }
    }
  }
}
