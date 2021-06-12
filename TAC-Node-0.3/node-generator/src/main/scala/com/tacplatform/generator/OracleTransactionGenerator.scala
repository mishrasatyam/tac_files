package com.tacplatform.generator

import cats.Show
import com.tacplatform.account.KeyPair
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.generator.OracleTransactionGenerator.Settings
import com.tacplatform.generator.utils.Gen
import com.tacplatform.generator.utils.Implicits.DoubleExt
import com.tacplatform.lang.v1.estimator.ScriptEstimator
import com.tacplatform.state._
import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.smart.SetScriptTransaction
import com.tacplatform.transaction.transfer.TransferTransaction
import com.tacplatform.transaction.{DataTransaction, Transaction}

class OracleTransactionGenerator(settings: Settings, val accounts: Seq[KeyPair], estimator: ScriptEstimator) extends TransactionGenerator {
  override def next(): Iterator[Transaction] = generate(settings).iterator

  def generate(settings: Settings): Seq[Transaction] = {
    val oracle = accounts.last

    val scriptedAccount = accounts.head

    val script = Gen.oracleScript(oracle, settings.requiredData, estimator)

    val enoughFee = 0.005.tac

    val setScript: Transaction =
      SetScriptTransaction
        .selfSigned(1.toByte, scriptedAccount, Some(script), enoughFee, timestamp = System.currentTimeMillis())
        .explicitGet()

    val setDataTx: Transaction = DataTransaction
      .selfSigned(1.toByte, oracle, settings.requiredData.toList, enoughFee, System.currentTimeMillis())
      .explicitGet()

    val now = System.currentTimeMillis()
    val transactions: List[Transaction] = (1 to settings.transactions).map { i =>
      TransferTransaction
        .selfSigned(2.toByte, scriptedAccount, oracle.toAddress, Tac, 1.tac, Tac, enoughFee, ByteStr.empty, now + i)
        .explicitGet()
    }.toList

    setScript +: setDataTx +: transactions
  }
}

object OracleTransactionGenerator {
  final case class Settings(transactions: Int, requiredData: Set[DataEntry[_]])

  object Settings {
    implicit val toPrintable: Show[Settings] = { x =>
      s"Transactions: ${x.transactions}\n" +
        s"DataEntries: ${x.requiredData}\n"
    }
  }
}
