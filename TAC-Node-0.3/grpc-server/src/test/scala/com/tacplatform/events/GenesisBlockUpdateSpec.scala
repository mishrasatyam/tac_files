//package com.tacplatform.events
//
//import com.tacplatform.common.utils.EitherExt2
//import com.tacplatform.settings.TacSettings
//import com.tacplatform.state.diffs.ENOUGH_AMT
//import com.tacplatform.transaction.GenesisTransaction
//import com.tacplatform.{BlockGen, TestHelpers}
//import org.scalacheck.Gen
//import org.scalatest.{FreeSpec, Matchers}
//import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
//
//class GenesisBlockUpdateSpec extends FreeSpec with Matchers with BlockGen with ScalaCheckPropertyChecks with EventsHelpers {
//  override protected def settings: TacSettings = TestHelpers.enableNG(super.settings)
//
//  val genesisAppendWithTacAmountGen: Gen[(BlockAppended, Long)] = for {
//    master      <- accountGen
//    tacAmount <- Gen.choose(1L, ENOUGH_AMT)
//    gt = GenesisTransaction.create(master.toAddress, tacAmount, 0).explicitGet()
//    b <- blockGen(Seq(gt), master)
//    ba = appendBlock(b)
//  } yield (ba, tacAmount)
//
//  "on genesis block append" - {
//    "master address balance gets correctly updated" in forAll(genesisAppendWithTacAmountGen) {
//      case (BlockAppended(_, _, _, _, _, upds), tacAmount) =>
//        upds.head.balances.head._3 shouldBe tacAmount
//    }
//
//    "updated Tac amount is calculated correctly" in forAll(genesisAppendWithTacAmountGen) {
//      case (BlockAppended(_, _, _, updatedTacAmount, _, _), tacAmount) =>
//        updatedTacAmount shouldBe tacAmount
//    }
//  }
//
//}
