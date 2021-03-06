package com.tacplatform.state.diffs

import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.db.WithState
import com.tacplatform.settings.TestFunctionalitySettings.Enabled
import com.tacplatform.state._
import com.tacplatform.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class CommonValidationTimeTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink with WithState {

  property("disallows too old transacions") {
    forAll(for {
      prevBlockTs <- timestampGen
      blockTs     <- Gen.choose(prevBlockTs, prevBlockTs + 7 * 24 * 3600 * 1000)
      master      <- accountGen
      height      <- positiveIntGen
      recipient   <- accountGen
      amount      <- positiveLongGen
      fee         <- smallFeeGen
      transfer1 = createTacTransfer(master, recipient.toAddress, amount, fee, prevBlockTs - Enabled.maxTransactionTimeBackOffset.toMillis - 1)
        .explicitGet()
    } yield (prevBlockTs, blockTs, height, transfer1)) {
      case (prevBlockTs, blockTs, height, transfer1) =>
        withLevelDBWriter(Enabled) { blockchain: Blockchain =>
          val result = TransactionDiffer(Some(prevBlockTs), blockTs)(blockchain, transfer1).resultE
          result should produce("in the past relative to previous block timestamp")
        }
    }
  }

  property("disallows transactions from far future") {
    forAll(for {
      prevBlockTs <- timestampGen
      blockTs     <- Gen.choose(prevBlockTs, prevBlockTs + 7 * 24 * 3600 * 1000)
      master      <- accountGen
      height      <- positiveIntGen
      recipient   <- accountGen
      amount      <- positiveLongGen
      fee         <- smallFeeGen
      transfer1 = createTacTransfer(master, recipient.toAddress, amount, fee, blockTs + Enabled.maxTransactionTimeForwardOffset.toMillis + 1)
        .explicitGet()
    } yield (prevBlockTs, blockTs, height, transfer1)) {
      case (prevBlockTs, blockTs, height, transfer1) =>
        val functionalitySettings = Enabled.copy(lastTimeBasedForkParameter = blockTs - 1)
        withLevelDBWriter(functionalitySettings) { blockchain: Blockchain =>
          TransactionDiffer(Some(prevBlockTs), blockTs)(blockchain, transfer1).resultE should
            produce("in the future relative to block timestamp")
        }
    }
  }
}
