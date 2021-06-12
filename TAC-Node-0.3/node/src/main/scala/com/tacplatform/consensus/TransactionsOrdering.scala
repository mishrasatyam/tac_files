package com.tacplatform.consensus

import com.tacplatform.transaction.Asset.Tac
import com.tacplatform.transaction.smart.InvokeScriptTransaction
import com.tacplatform.transaction.{Authorized, Transaction}

object TransactionsOrdering {
  trait TacOrdering extends Ordering[Transaction] {
    def isWhitelisted(t: Transaction): Boolean = false
    def txTimestampOrder(ts: Long): Long
    private def orderBy(t: Transaction): (Boolean, Double, Long, Long) = {
      val byWhiteList = !isWhitelisted(t) // false < true
      val size        = t.bytes().length
      val byFee       = if (t.assetFee._1 != Tac) 0 else -t.assetFee._2
      val byTimestamp = txTimestampOrder(t.timestamp)

      (byWhiteList, byFee.toDouble / size.toDouble, byFee, byTimestamp)
    }
    override def compare(first: Transaction, second: Transaction): Int = {
      import Ordering.Double.TotalOrdering
      implicitly[Ordering[(Boolean, Double, Long, Long)]].compare(orderBy(first), orderBy(second))
    }
  }

  object InBlock extends TacOrdering {
    // sorting from network start
    override def txTimestampOrder(ts: Long): Long = -ts
  }

  case class InUTXPool(whitelistAddresses: Set[String]) extends TacOrdering {
    override def isWhitelisted(t: Transaction): Boolean =
      t match {
        case _ if whitelistAddresses.isEmpty                                                            => false
        case a: Authorized if whitelistAddresses.contains(a.sender.toAddress.stringRepr)                => true
        case i: InvokeScriptTransaction if whitelistAddresses.contains(i.dAppAddressOrAlias.stringRepr) => true
        case _                                                                                          => false
      }
    override def txTimestampOrder(ts: Long): Long = ts
  }
}
