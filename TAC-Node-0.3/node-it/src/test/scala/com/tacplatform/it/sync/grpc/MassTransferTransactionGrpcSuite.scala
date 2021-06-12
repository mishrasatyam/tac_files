package com.tacplatform.it.sync.grpc

import com.google.protobuf.ByteString
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.it.sync._
import com.tacplatform.protobuf.transaction.MassTransferTransactionData.Transfer
import com.tacplatform.protobuf.transaction.{PBTransactions, Recipient}
import com.tacplatform.transaction.transfer.MassTransferTransaction.MaxTransferCount
import com.tacplatform.transaction.transfer.TransferTransaction.MaxAttachmentSize
import io.grpc.Status.Code

class MassTransferTransactionGrpcSuite extends GrpcBaseTransactionSuite {

  test("asset mass transfer changes asset balances and sender's.tac balance is decreased by fee.") {
    for (v <- massTransferTxSupportedVersions) {
      val firstBalance = sender.tacBalance(firstAddress)
      val secondBalance = sender.tacBalance(secondAddress)
      val attachment = ByteString.copyFrom("mass transfer description".getBytes("UTF-8"))

      val transfers = List(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), transferAmount))
      val assetId = PBTransactions.vanilla(
        sender.broadcastIssue(firstAcc, "name", issueAmount, 8, reissuable = false, issueFee, waitForTx = true)
      ).explicitGet().id().toString
      sender.waitForTransaction(assetId)

      val massTransferTransactionFee = calcMassTransferFee(transfers.size)
      sender.broadcastMassTransfer(firstAcc, Some(assetId), transfers, attachment, massTransferTransactionFee, waitForTx = true)

      val firstBalanceAfter = sender.tacBalance(firstAddress)
      val secondBalanceAfter = sender.tacBalance(secondAddress)

      firstBalanceAfter.regular shouldBe firstBalance.regular - issueFee - massTransferTransactionFee
      firstBalanceAfter.effective shouldBe firstBalance.effective - issueFee - massTransferTransactionFee
      sender.assetsBalance(firstAddress, Seq(assetId)).getOrElse(assetId, 0L) shouldBe issueAmount - transferAmount
      secondBalanceAfter.regular shouldBe secondBalance.regular
      secondBalanceAfter.effective shouldBe secondBalance.effective
      sender.assetsBalance(secondAddress, Seq(assetId)).getOrElse(assetId, 0L) shouldBe transferAmount
    }
  }

  test("tac mass transfer changes tac balances") {
    val firstBalance = sender.tacBalance(firstAddress)
    val secondBalance = sender.tacBalance(secondAddress)
    val thirdBalance = sender.tacBalance(thirdAddress)
    val transfers = List(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), transferAmount), Transfer(Some(Recipient().withPublicKeyHash(thirdAddress)), 2 * transferAmount))

    val massTransferTransactionFee = calcMassTransferFee(transfers.size)
    sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee, waitForTx = true)

    val firstBalanceAfter = sender.tacBalance(firstAddress)
    val secondBalanceAfter = sender.tacBalance(secondAddress)
    val thirdBalanceAfter = sender.tacBalance(thirdAddress)

    firstBalanceAfter.regular shouldBe firstBalance.regular - massTransferTransactionFee - 3 * transferAmount
    firstBalanceAfter.effective shouldBe firstBalance.effective - massTransferTransactionFee - 3 * transferAmount
    secondBalanceAfter.regular shouldBe secondBalance.regular + transferAmount
    secondBalanceAfter.effective shouldBe secondBalance.effective + transferAmount
    thirdBalanceAfter.regular shouldBe thirdBalance.regular + 2 * transferAmount
    thirdBalanceAfter.effective shouldBe thirdBalance.effective + 2 * transferAmount
  }

  test("can not make mass transfer without having enough tac") {
    val firstBalance = sender.tacBalance(firstAddress)
    val secondBalance = sender.tacBalance(secondAddress)
    val transfers        = List(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), firstBalance.regular / 2), Transfer(Some(Recipient().withPublicKeyHash(thirdAddress)), firstBalance.regular / 2))

    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = calcMassTransferFee(transfers.size)),
      "Attempt to transfer unavailable funds",
      Code.INVALID_ARGUMENT
    )

    nodes.foreach(n => n.waitForHeight(n.height + 1))
    sender.tacBalance(firstAddress) shouldBe firstBalance
    sender.tacBalance(secondAddress) shouldBe secondBalance
  }

  test("cannot make mass transfer when fee less then minimal ") {
    val firstBalance = sender.tacBalance(firstAddress)
    val secondBalance = sender.tacBalance(secondAddress)
    val transfers        = List(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), transferAmount))
    val massTransferTransactionFee = calcMassTransferFee(transfers.size)

    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee - 1),
      s"does not exceed minimal value of $massTransferTransactionFee TAC",
      Code.INVALID_ARGUMENT
    )

    nodes.foreach(n => n.waitForHeight(n.height + 1))
    sender.tacBalance(firstAddress) shouldBe firstBalance
    sender.tacBalance(secondAddress) shouldBe secondBalance
  }

  test("cannot make mass transfer without having enough of effective balance") {
    val firstBalance = sender.tacBalance(firstAddress)
    val secondBalance = sender.tacBalance(secondAddress)
    val transfers        = List(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), firstBalance.regular - 2 * minFee))
    val massTransferTransactionFee = calcMassTransferFee(transfers.size)

    sender.broadcastLease(firstAcc, Recipient().withPublicKeyHash(secondAddress), leasingAmount, minFee, waitForTx = true)

    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee),
      "Attempt to transfer unavailable funds",
      Code.INVALID_ARGUMENT
    )
    nodes.foreach(n => n.waitForHeight(n.height + 1))
    sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular - minFee
    sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective - minFee - leasingAmount
    sender.tacBalance(secondAddress).regular shouldBe secondBalance.regular
    sender.tacBalance(secondAddress).effective shouldBe secondBalance.effective + leasingAmount
  }

  test("cannot broadcast invalid mass transfer tx") {
    val firstBalance = sender.tacBalance(firstAddress)
    val secondBalance = sender.tacBalance(secondAddress)
    val defaultTransfer = List(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), transferAmount))

    val negativeTransfer = List(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), -1))
    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = negativeTransfer, fee = calcMassTransferFee(negativeTransfer.size)),
      "One of the transfers has negative amount",
      Code.INVALID_ARGUMENT
    )

    val tooManyTransfers = List.fill(MaxTransferCount + 1)(Transfer(Some(Recipient().withPublicKeyHash(secondAddress)), 1))
    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = tooManyTransfers, fee = calcMassTransferFee(MaxTransferCount + 1)),
      s"Number of transfers ${MaxTransferCount + 1} is greater than 100",
      Code.INVALID_ARGUMENT
    )

    val tooBigAttachment = ByteString.copyFrom(("a" * (MaxAttachmentSize + 1)).getBytes("UTF-8"))
    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = defaultTransfer, attachment = tooBigAttachment, fee = calcMassTransferFee(1)),
      "Too big sequence requested",
      Code.INVALID_ARGUMENT
    )

    sender.tacBalance(firstAddress) shouldBe firstBalance
    sender.tacBalance(secondAddress) shouldBe secondBalance
  }

  test("huge transactions are allowed") {
    val firstBalance = sender.tacBalance(firstAddress)
    val fee = calcMassTransferFee(MaxTransferCount)
    val amount = (firstBalance.available - fee) / MaxTransferCount
    val maxAttachment = ByteString.copyFrom(("a" * MaxAttachmentSize).getBytes("UTF-8"))


    val transfers  = List.fill(MaxTransferCount)(Transfer(Some(Recipient().withPublicKeyHash(firstAddress)), amount))
    sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = fee, attachment = maxAttachment, waitForTx = true)

    sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular - fee
    sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective - fee
  }

  test("able to mass transfer to alias") {
    val firstBalance = sender.tacBalance(firstAddress)
    val secondBalance = sender.tacBalance(secondAddress)

    val alias = "masstest_alias"

    sender.broadcastCreateAlias(secondAcc, alias, minFee, waitForTx = true)

    val transfers = List(Transfer(Some(Recipient().withPublicKeyHash(firstAddress)), transferAmount), Transfer(Some(Recipient().withAlias(alias)), transferAmount))

    val massTransferTransactionFee = calcMassTransferFee(transfers.size)
    sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee, waitForTx = true)

    sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular - massTransferTransactionFee - transferAmount
    sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective - massTransferTransactionFee - transferAmount
    sender.tacBalance(secondAddress).regular shouldBe secondBalance.regular + transferAmount - minFee
    sender.tacBalance(secondAddress).effective shouldBe secondBalance.effective + transferAmount - minFee
  }
}
