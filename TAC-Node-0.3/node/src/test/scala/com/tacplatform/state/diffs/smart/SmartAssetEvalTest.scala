package com.tacplatform.state.diffs.smart

import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithState
import com.tacplatform.lang.directives.values.{Expression, V3}
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.utils._
import com.tacplatform.lang.v1.compiler.ExpressionCompiler
import com.tacplatform.lang.v1.parser.Parser
import com.tacplatform.state.diffs._
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.assets.{IssueTransaction, SetAssetScriptTransaction}
import com.tacplatform.transaction.transfer._
import com.tacplatform.transaction.{GenesisTransaction, TxVersion}
import com.tacplatform.utils._
import com.tacplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class SmartAssetEvalTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {
  val preconditions: Gen[(GenesisTransaction, IssueTransaction, SetAssetScriptTransaction, TransferTransaction)] =
    for {
      firstAcc  <- accountGen
      secondAcc <- accountGen
      ts        <- timestampGen
      genesis = GenesisTransaction.create(firstAcc.toAddress, ENOUGH_AMT, ts).explicitGet()

      emptyScript = s"""
                       |{-# STDLIB_VERSION 3 #-}
                       |{-# CONTENT_TYPE EXPRESSION #-}
                       |{-# SCRIPT_TYPE ASSET #-}
                       |
                       |true
                       |
        """.stripMargin

      parsedEmptyScript = Parser.parseExpr(emptyScript).get.value

      emptyExprScript = ExprScript(V3, ExpressionCompiler(compilerContext(V3, Expression, isAssetScript = true), parsedEmptyScript).explicitGet()._1)
        .explicitGet()

      issueTransaction = IssueTransaction(
          TxVersion.V2,
          firstAcc.publicKey,
          "name".utf8Bytes,
          "description".utf8Bytes,
          100,
          0,
          false,
          Some(emptyExprScript),
          1000000,
          ts
        ).signWith(firstAcc.privateKey)

      asset = IssuedAsset(issueTransaction.id())

      assetScript = s"""
                       | {-# STDLIB_VERSION 3 #-}
                       | {-# CONTENT_TYPE EXPRESSION #-}
                       | {-# SCRIPT_TYPE ASSET #-}
                       |
                       | this.id         == base58'${asset.id.toString}' &&
                       | this.quantity   == 100                        &&
                       | this.decimals   == 0                          &&
                       | this.reissuable == false                      &&
                       | this.scripted   == true                       &&
                       | this.sponsored  == false
                       |
        """.stripMargin

      untypedScript = Parser.parseExpr(assetScript).get.value

      typedScript = ExprScript(V3, ExpressionCompiler(compilerContext(V3, Expression, isAssetScript = true), untypedScript).explicitGet()._1)
        .explicitGet()

      setAssetScriptTransaction = SetAssetScriptTransaction
        .signed(1.toByte, firstAcc.publicKey, asset, Some(typedScript), 1000, ts + 10, firstAcc.privateKey)
        .explicitGet()

      assetTransferTransaction = TransferTransaction.selfSigned(1.toByte, firstAcc, secondAcc.toAddress, asset, 1, Tac, 1000, ByteStr.empty,  ts + 20)
        .explicitGet()

    } yield (genesis, issueTransaction, setAssetScriptTransaction, assetTransferTransaction)

  property("Smart asset with scrtipt that contains 'this' link") {
    forAll(preconditions) {
      case (genesis, issueTransaction, setAssetScriptTransaction, assetTransferTransaction) =>
        assertDiffAndState(smartEnabledFS) { append =>
          append(Seq(genesis)).explicitGet()
          append(Seq(issueTransaction)).explicitGet()
          append(Seq(setAssetScriptTransaction)).explicitGet()
          append(Seq(assetTransferTransaction)).explicitGet()
        }
    }
  }
}
