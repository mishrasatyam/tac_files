package com.tacplatform.transaction.smart

import cats.Id
import cats.implicits._
import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.directives.values.{ContentType, ScriptType, StdLibVersion}
import com.tacplatform.lang.v1.CTX
import com.tacplatform.lang.v1.evaluator.ctx.EvaluationContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.traits.Environment
import com.tacplatform.lang.{ExecutionError, Global}
import com.tacplatform.state._
import monix.eval.Coeval

import java.util

object BlockchainContext {

  type In = TacEnvironment.In

  private[this] val cache = new util.HashMap[(StdLibVersion, DirectiveSet), CTX[Environment]]()

  def build(
      version: StdLibVersion,
      nByte: Byte,
      in: Coeval[Environment.InputEntity],
      h: Coeval[Int],
      blockchain: Blockchain,
      isTokenContext: Boolean,
      isContract: Boolean,
      address: Environment.Tthis,
      txId: ByteStr
  ): Either[ExecutionError, EvaluationContext[Environment, Id]] =
    DirectiveSet(
      version,
      ScriptType.isAssetScript(isTokenContext),
      ContentType.isDApp(isContract)
    ).map(
      ds => build(ds, new TacEnvironment(nByte, in, h, blockchain, address, ds, txId))
    )

  def build(
      ds: DirectiveSet,
      environment: Environment[Id]
  ): EvaluationContext[Environment, Id] =
    cache
      .synchronized(
        cache.computeIfAbsent(
          (ds.stdLibVersion, ds), { _ =>
            PureContext.build(ds.stdLibVersion).withEnvironment[Environment] |+|
              CryptoContext.build(Global, ds.stdLibVersion).withEnvironment[Environment] |+|
              TacContext.build(Global, ds)
          }
        )
      )
      .evaluationContext(environment)
}
