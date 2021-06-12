package com.tacplatform.state.diffs.smart.scenarios

import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithState
import com.tacplatform.lagonaki.mocks.TestBlock
import com.tacplatform.lang.Global.MaxBase58Bytes
import com.tacplatform.lang.directives.values._
import com.tacplatform.lang.script.v1.ExprScript
import com.tacplatform.lang.utils._
import com.tacplatform.lang.v1.compiler.ExpressionCompiler
import com.tacplatform.lang.v1.parser.Parser
import com.tacplatform.state.diffs.TransactionDiffer.TransactionValidationError
import com.tacplatform.state.diffs._
import com.tacplatform.state.diffs.smart.smartEnabledFS
import com.tacplatform.transaction.TxValidationError.ScriptExecutionError
import com.tacplatform.transaction.smart.SetScriptTransaction
import com.tacplatform.transaction.transfer._
import com.tacplatform.transaction.{CreateAliasTransaction, DataTransaction, GenesisTransaction, Proofs}
import com.tacplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class OracleDataTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink with Matchers {
  val preconditions
      : Gen[(GenesisTransaction, GenesisTransaction, CreateAliasTransaction, SetScriptTransaction, DataTransaction, TransferTransaction)] =
    for {
      master <- accountGen
      oracle <- accountGen
      alice  <- accountGen
      ts     <- positiveIntGen
      genesis  = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      genesis2 = GenesisTransaction.create(oracle.toAddress, ENOUGH_AMT, ts).explicitGet()
      alias           <- aliasGen
      createAlias     <- createAliasGen(oracle, alias, 400000, System.currentTimeMillis())
      long            <- longEntryGen(dataAsciiKeyGen)
      bool            <- booleanEntryGen(dataAsciiKeyGen).filter(_.key != long.key)
      bin             <- binaryEntryGen(MaxBase58Bytes, dataAsciiKeyGen).filter(e => e.key != long.key && e.key != bool.key)
      str             <- stringEntryGen(500, dataAsciiKeyGen).filter(e => e.key != long.key && e.key != bool.key && e.key != bin.key)
      dataTransaction <- dataTransactionGenP(oracle, List(long, bool, bin, str))
      allFieldsRequiredScript = s"""
                                   | match tx {
                                   | case t : DataTransaction =>
                                   |   let txId = match extract(transactionById(t.id)) {
                                   |     case d: DataTransaction => d.bodyBytes == base64'${ByteStr(dataTransaction.bodyBytes.apply()).base64}'
                                   |     case _ => false
                                   |   }
                                   |   let txHeightId = extract(transactionHeightById(t.id)) > 0
                                   |   txId && txHeightId
                                   | case _ : CreateAliasTransaction => true
                                   | case _ =>
                                   |   let oracle = Alias("${alias.name}")
                                   |   let long = extract(getInteger(oracle,"${long.key}")) == ${long.value}
                                   |   let bool = extract(getBoolean(oracle,"${bool.key}")) == ${bool.value}
                                   |   let bin = extract(getBinary(oracle,"${bin.key}")) == base58'${bin.value.toString}'
                                   |   let str = extract(getString(oracle,"${str.key}")) == "${str.value}"
                                   |   long && bool && bin && str
                                   |}""".stripMargin
      setScript <- {
        val untypedAllFieldsRequiredScript = Parser.parseExpr(allFieldsRequiredScript).get.value
        val typedAllFieldsRequiredScript =
          ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untypedAllFieldsRequiredScript).explicitGet()._1
        selfSignedSetScriptTransactionGenP(master, ExprScript(typedAllFieldsRequiredScript).explicitGet())
      }
      transferFromScripted <- versionedTransferGenP(master.publicKey, alice.toAddress, Proofs.empty)

    } yield (genesis, genesis2, createAlias, setScript, dataTransaction, transferFromScripted)

  property("simple oracle value required to transfer") {
    forAll(preconditions) {
      case (genesis, genesis2, createAlias, setScript, dataTransaction, transferFromScripted) =>
        assertDiffAndState(
          Seq(TestBlock.create(Seq(genesis, genesis2, createAlias, setScript, dataTransaction))),
          TestBlock.create(Seq(transferFromScripted)),
          smartEnabledFS
        ) { case _ => () }
        assertDiffEi(
          Seq(TestBlock.create(Seq(genesis, genesis2, createAlias, setScript))),
          TestBlock.create(Seq(transferFromScripted)),
          smartEnabledFS
        )(_ should matchPattern { case Left(TransactionValidationError(_: ScriptExecutionError, _)) => })
    }
  }
}
