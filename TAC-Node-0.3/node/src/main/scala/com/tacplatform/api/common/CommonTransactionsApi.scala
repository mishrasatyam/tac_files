package com.tacplatform.api.common

import com.tacplatform.account.{Address, AddressOrAlias}
import com.tacplatform.api.{BlockMeta, common}
import com.tacplatform.block
import com.tacplatform.block.Block
import com.tacplatform.block.Block.TransactionProof
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.state.diffs.FeeValidation
import com.tacplatform.state.diffs.FeeValidation.FeeDetails
import com.tacplatform.state.{Blockchain, Diff, Height, InvokeScriptResult}
import com.tacplatform.transaction.smart.InvokeScriptTransaction
import com.tacplatform.transaction.smart.script.trace.TracedResult
import com.tacplatform.transaction.{Asset, CreateAliasTransaction, Transaction}
import com.tacplatform.utx.UtxPool
import com.tacplatform.wallet.Wallet
import monix.reactive.Observable
import org.iq80.leveldb.DB

import scala.concurrent.Future

trait CommonTransactionsApi {
  import CommonTransactionsApi._

  def aliasesOfAddress(address: Address): Observable[(Height, CreateAliasTransaction)]

  def transactionById(txId: ByteStr): Option[TransactionMeta]

  def unconfirmedTransactions: Seq[Transaction]

  def unconfirmedTransactionById(txId: ByteStr): Option[Transaction]

  def calculateFee(tx: Transaction): Either[ValidationError, (Asset, Long, Long)]

  def broadcastTransaction(tx: Transaction): Future[TracedResult[ValidationError, Boolean]]

  def transactionsByAddress(
      subject: AddressOrAlias,
      sender: Option[Address],
      transactionTypes: Set[Byte],
      fromId: Option[ByteStr] = None
  ): Observable[TransactionMeta]

  def transactionProofs(transactionIds: List[ByteStr]): List[TransactionProof]
}

object CommonTransactionsApi {
  sealed trait TransactionMeta {
    def height: Height
    def transaction: Transaction
    def succeeded: Boolean
  }

  object TransactionMeta {
    final case class Default(height: Height, transaction: Transaction, succeeded: Boolean) extends TransactionMeta

    final case class Invoke(height: Height, transaction: InvokeScriptTransaction, succeeded: Boolean, invokeScriptResult: Option[InvokeScriptResult])
        extends TransactionMeta

    def unapply(tm: TransactionMeta): Option[(Height, Transaction, Boolean)] =
      Some((tm.height, tm.transaction, tm.succeeded))

    def create(height: Height, transaction: Transaction, succeeded: Boolean)(
        result: InvokeScriptTransaction => Option[InvokeScriptResult]
    ): TransactionMeta =
      transaction match {
        case ist: InvokeScriptTransaction =>
          Invoke(height, ist, succeeded, result(ist))

        case _ =>
          Default(height, transaction, succeeded)
      }
  }

  def apply(
      maybeDiff: => Option[(Height, Diff)],
      db: DB,
      blockchain: Blockchain,
      utx: UtxPool,
      wallet: Wallet,
      publishTransaction: Transaction => Future[TracedResult[ValidationError, Boolean]],
      blockAt: Int => Option[(BlockMeta, Seq[Transaction])]
  ): CommonTransactionsApi = new CommonTransactionsApi {
    private def resolve(subject: AddressOrAlias): Option[Address] = blockchain.resolveAlias(subject).toOption

    override def aliasesOfAddress(address: Address): Observable[(Height, CreateAliasTransaction)] = common.aliasesOfAddress(db, maybeDiff, address)

    override def transactionsByAddress(
        subject: AddressOrAlias,
        sender: Option[Address],
        transactionTypes: Set[Byte],
        fromId: Option[ByteStr] = None
    ): Observable[TransactionMeta] = resolve(subject).fold(Observable.empty[TransactionMeta]) { subjectAddress =>
      common.addressTransactions(db, maybeDiff, subjectAddress, sender, transactionTypes, fromId)
    }

    override def transactionById(transactionId: ByteStr): Option[TransactionMeta] =
      blockchain.transactionInfo(transactionId).map {
        case (height, transaction, succeeded) =>
          TransactionMeta.create(Height(height), transaction, succeeded) { _ =>
            maybeDiff
              .flatMap { case (_, diff) => diff.scriptResults.get(transactionId) }
              .orElse(AddressTransactions.loadInvokeScriptResult(db, transactionId))
          }
      }

    override def unconfirmedTransactions: Seq[Transaction] = utx.all

    override def unconfirmedTransactionById(transactionId: ByteStr): Option[Transaction] =
      utx.transactionById(transactionId)

    override def calculateFee(tx: Transaction): Either[ValidationError, (Asset, Long, Long)] =
      FeeValidation
        .getMinFee(blockchain, tx)
        .map {
          case FeeDetails(asset, _, feeInAsset, feeInTac) =>
            (asset, feeInAsset, feeInTac)
        }

    override def broadcastTransaction(tx: Transaction): Future[TracedResult[ValidationError, Boolean]] = publishTransaction(tx)

    override def transactionProofs(transactionIds: List[ByteStr]): List[TransactionProof] =
      for {
        transactionId            <- transactionIds
        (height, transaction, _) <- blockchain.transactionInfo(transactionId)
        (meta, allTransactions)  <- blockAt(height) if meta.header.version >= Block.ProtoBlockVersion
        transactionProof         <- block.transactionProof(transaction, allTransactions)
      } yield transactionProof
  }
}
