package com.tacplatform.features

import cats.implicits._
import com.tacplatform.TestTime
import com.tacplatform.block.Block
import com.tacplatform.common.state.diffs.ProduceError.produce
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithDomain
import com.tacplatform.it.util._
import com.tacplatform.lang.Global
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.lang.directives.values.{Account, DApp => DAppType, _}
import com.tacplatform.lang.script.{ContractScript, Script}
import com.tacplatform.lang.v1.compiler.ContractCompiler
import com.tacplatform.lang.v1.evaluator.ctx.impl.tac.TacContext
import com.tacplatform.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.tacplatform.lang.v1.traits.Environment
import com.tacplatform.mining.MiningConstraints.MaxScriptsComplexityInBlock
import com.tacplatform.mining._
import com.tacplatform.state.diffs.BlockDiffer
import com.tacplatform.transaction.TxHelpers
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.{FlatSpec, Matchers}

class RideV5LimitsChangeTest extends FlatSpec with Matchers with WithDomain with PathMockFactory {
  "Blockchain" should "reject block with >1kk complexity before SynchronousCalls activated" in withDomain(DomainPresets.RideV4) { d =>
    val contractSigner  = TxHelpers.secondSigner
    val contractAddress = contractSigner.toAddress
    d.appendBlock(TxHelpers.genesis(TxHelpers.defaultAddress), TxHelpers.genesis(contractAddress, 10 tac))

    val setScript = TxHelpers.setScript(contractSigner, contract)
    d.appendBlock(setScript)

    val invokes = for (_ <- 1 to 273) yield TxHelpers.invoke(contractAddress, "test") // 3675 complexity, 1003275 total

    val block = d.createBlock(Block.ProtoBlockVersion, invokes, strictTime = true)
    val differResult = BlockDiffer.fromBlock(
      d.blockchain,
      Some(d.lastBlock),
      block,
      MiningConstraints(d.blockchain, d.blockchain.height, Some(SettingsFromDefaultConfig.minerSettings)).total
    )
    differResult should produce("Limit of txs was reached")
  }

  it should "accept block with 2.5kk complexity after SynchronousCalls activated" in withDomain(DomainPresets.RideV5) { d =>
    val contractSigner  = TxHelpers.secondSigner
    val contractAddress = contractSigner.toAddress
    d.appendBlock(TxHelpers.genesis(TxHelpers.defaultAddress), TxHelpers.genesis(contractAddress, 10 tac))

    val setScript = TxHelpers.setScript(contractSigner, contract)
    d.appendBlock(setScript)

    val invokesCount     = 680
    val invokeComplexity = 3620
    val invokes          = for (_ <- 1 to invokesCount) yield TxHelpers.invoke(contractAddress, "test")

    val time       = new TestTime()

    val block = d.createBlock(Block.ProtoBlockVersion, invokes, strictTime = true)
    val differResult = BlockDiffer
      .fromBlock(
        d.blockchain,
        Some(d.lastBlock),
        block,
        MiningConstraints(d.blockchain, d.blockchain.height, Some(SettingsFromDefaultConfig.minerSettings)).total
      )
      .explicitGet()
    differResult.constraint.asInstanceOf[MultiDimensionalMiningConstraint].constraints.head shouldBe OneDimensionalMiningConstraint(
      rest = MaxScriptsComplexityInBlock.AfterRideV5 - invokesCount * invokeComplexity,
      TxEstimators.scriptsComplexity,
      "MaxScriptsComplexityInBlock"
    )

    time.setTime(block.header.timestamp)
    d.appendBlock(block)
    d.blockchain.height shouldBe 3
  }

  private[this] val contract: Script = {
    val ctx = {
      val directives = DirectiveSet(V4, Account, DAppType).explicitGet()
      PureContext.build(V4).withEnvironment[Environment] |+|
        CryptoContext.build(Global, V4).withEnvironment[Environment] |+|
        TacContext.build(Global, directives)
    }

    val script =
      s"""
        | {-#STDLIB_VERSION 4 #-}
        | {-#SCRIPT_TYPE ACCOUNT #-}
        | {-#CONTENT_TYPE DAPP #-}
        |
        | @Callable(tx)
        | func test() =
        |   if (${"sigVerify(base58'', base58'', base58'') ||" * 18} false) then []
        |   else []
        |
        | @Verifier(tx)
        | func verify() =
        |   ${"sigVerify(base58'', base58'', base58'') ||" * 9} true
      """.stripMargin

    val dApp = ContractCompiler.compile(script, ctx.compilerContext, V4).explicitGet()
    ContractScript(V4, dApp).explicitGet()
  }
}
