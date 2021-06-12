package com.tacplatform.http

import com.tacplatform.lang.ValidationError
import com.tacplatform.network.TransactionPublisher
import com.tacplatform.transaction.Transaction
import com.tacplatform.transaction.smart.script.trace.TracedResult

import scala.concurrent.Future

object DummyTransactionPublisher {
  val accepting: TransactionPublisher = { (_, _) =>
    Future.successful(TracedResult(Right(true)))
  }

  def rejecting(error: Transaction => ValidationError): TransactionPublisher = { (tx, _) =>
    Future.successful(TracedResult(Left(error(tx))))
  }
}
