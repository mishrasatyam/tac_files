package com.tacplatform.transaction

import cats.implicits._
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ExecutionError
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.directives.values.{Account, Expression, Asset => AssetType, DApp => DAppType}
import com.tacplatform.lang.v1.traits.Environment.{InputEntity, Tthis}
import com.tacplatform.state.Blockchain
import com.tacplatform.transaction.smart.script.ScriptRunner.TxOrd
import com.tacplatform.transaction.smart.{DApp => DAppTarget}
import shapeless._

package object smart {
  def buildThisValue(
      in: TxOrd,
      blockchain: Blockchain,
      ds: DirectiveSet,
      scriptContainerAddress: Tthis
  ): Either[ExecutionError, InputEntity] =
    in.eliminate(
      tx =>
        RealTransactionWrapper(tx, blockchain, ds.stdLibVersion, paymentTarget(ds, scriptContainerAddress))
          .map(Coproduct[InputEntity](_)),
      _.eliminate(
        order => Coproduct[InputEntity](RealTransactionWrapper.ord(order)).asRight[ExecutionError],
        _.eliminate(
          scriptTransfer => Coproduct[InputEntity](scriptTransfer).asRight[ExecutionError],
          _ => ???
        )
      )
    )

  def paymentTarget(
      ds: DirectiveSet,
      scriptContainerAddress: Tthis
  ): AttachedPaymentTarget =
    (ds.scriptType, ds.contentType) match {
      case (Account, DAppType)                 => DAppTarget
      case (Account, Expression)               => InvokerScript
      case (AssetType, Expression) => scriptContainerAddress.eliminate(_ => throw new Exception("Not a AssetId"), _.eliminate(a => AssetScript(ByteStr(a.id)), v => throw new Exception(s"Fail processing tthis value $v")))
      case _                                      => ???
    }
}
