package com.tacplatform.events

import com.tacplatform.lang.ValidationError
import com.tacplatform.state.Diff
import com.tacplatform.transaction.Transaction

sealed trait UtxEvent
object UtxEvent {
  final case class TxAdded(tx: Transaction, diff: Diff) extends UtxEvent {
    override def toString: String = s"TxAdded(${tx.id()})"
  }
  final case class TxRemoved(tx: Transaction, reason: Option[ValidationError]) extends UtxEvent {
    override def toString: String = s"TxRemoved(${tx.id()})"
  }
}
