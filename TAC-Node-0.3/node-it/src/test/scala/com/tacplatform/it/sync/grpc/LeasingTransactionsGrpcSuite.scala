package com.tacplatform.it.sync.grpc

import com.google.protobuf.ByteString
import com.tacplatform.api.grpc.LeaseResponse
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.it.sync._
import com.tacplatform.it.util._
import com.tacplatform.protobuf.transaction.{PBRecipients, PBTransactions, Recipient}
import com.tacplatform.transaction.Transaction
import com.tacplatform.transaction.lease.LeaseTransaction
import io.grpc.Status.Code

class LeasingTransactionsGrpcSuite extends GrpcBaseTransactionSuite {
  private val errorMessage = "Reason: Cannot lease more than own"

  test("leasing tac decreases lessor's eff.b. and increases lessee's eff.b.; lessor pays fee") {
    for (v <- leaseTxSupportedVersions) {
      val firstBalance  = sender.tacBalance(firstAddress)
      val secondBalance = sender.tacBalance(secondAddress)

      val leaseTx   = sender.broadcastLease(firstAcc, PBRecipients.create(secondAcc.toAddress), leasingAmount, minFee, version = v, waitForTx = true)
      val vanillaTx = PBTransactions.vanilla(leaseTx).explicitGet()
      val leaseTxId = vanillaTx.id().toString
      val height    = sender.getStatus(leaseTxId).height

      sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular - minFee
      sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective - minFee - leasingAmount
      sender.tacBalance(secondAddress).regular shouldBe secondBalance.regular
      sender.tacBalance(secondAddress).effective shouldBe secondBalance.effective + leasingAmount

      val response = toResponse(vanillaTx, height)
      sender.getActiveLeases(secondAddress) shouldBe List(response)
      sender.getActiveLeases(firstAddress) shouldBe List(response)

      sender.broadcastLeaseCancel(firstAcc, leaseTxId, minFee, waitForTx = true)
    }
  }

  test("cannot lease non-own tac") {
    for (v <- leaseTxSupportedVersions) {
      val leaseTx   = sender.broadcastLease(firstAcc, PBRecipients.create(secondAcc.toAddress), leasingAmount, minFee, version = v, waitForTx = true)
      val vanillaTx = PBTransactions.vanilla(leaseTx).explicitGet()
      val leaseTxId = vanillaTx.id().toString
      val height    = sender.getStatus(leaseTxId).height

      val secondEffBalance = sender.tacBalance(secondAddress).effective
      val thirdEffBalance  = sender.tacBalance(thirdAddress).effective

      assertGrpcError(
        sender.broadcastLease(secondAcc, PBRecipients.create(thirdAcc.toAddress), secondEffBalance - minFee, minFee, version = v),
        errorMessage,
        Code.INVALID_ARGUMENT
      )

      sender.tacBalance(secondAddress).effective shouldBe secondEffBalance
      sender.tacBalance(thirdAddress).effective shouldBe thirdEffBalance

      val response = toResponse(vanillaTx, height)
      sender.getActiveLeases(secondAddress) shouldBe List(response)
      sender.getActiveLeases(thirdAddress) shouldBe List.empty

      sender.broadcastLeaseCancel(firstAcc, leaseTxId, minFee, waitForTx = true)
    }
  }

  test("can not make leasing without having enough balance") {
    for (v <- leaseTxSupportedVersions) {
      val firstBalance  = sender.tacBalance(firstAddress)
      val secondBalance = sender.tacBalance(secondAddress)

      //secondAddress effective balance more than general balance
      assertGrpcError(
        sender.broadcastLease(secondAcc, Recipient().withPublicKeyHash(firstAddress), secondBalance.regular + 1.tac, minFee, version = v),
        errorMessage,
        Code.INVALID_ARGUMENT
      )

      assertGrpcError(
        sender.broadcastLease(firstAcc, Recipient().withPublicKeyHash(secondAddress), firstBalance.regular, minFee, version = v),
        "Accounts balance errors",
        Code.INVALID_ARGUMENT
      )

      assertGrpcError(
        sender.broadcastLease(firstAcc, Recipient().withPublicKeyHash(secondAddress), firstBalance.regular - minFee / 2, minFee, version = v),
        "Accounts balance errors",
        Code.INVALID_ARGUMENT
      )

      sender.tacBalance(firstAddress) shouldBe firstBalance
      sender.tacBalance(secondAddress) shouldBe secondBalance
      sender.getActiveLeases(firstAddress) shouldBe List.empty
      sender.getActiveLeases(secondAddress) shouldBe List.empty
    }
  }

  test("lease cancellation reverts eff.b. changes; lessor pays fee for both lease and cancellation") {
    for (v <- leaseTxSupportedVersions) {
      val firstBalance  = sender.tacBalance(firstAddress)
      val secondBalance = sender.tacBalance(secondAddress)

      val leaseTx   = sender.broadcastLease(firstAcc, PBRecipients.create(secondAcc.toAddress), leasingAmount, minFee, version = v, waitForTx = true)
      val leaseTxId = PBTransactions.vanilla(leaseTx).explicitGet().id().toString

      sender.broadcastLeaseCancel(firstAcc, leaseTxId, minFee, waitForTx = true)

      sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular - 2 * minFee
      sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective - 2 * minFee
      sender.tacBalance(secondAddress).regular shouldBe secondBalance.regular
      sender.tacBalance(secondAddress).effective shouldBe secondBalance.effective
      sender.getActiveLeases(secondAddress) shouldBe List.empty
      sender.getActiveLeases(firstAddress) shouldBe List.empty
    }
  }

  test("lease cancellation can be done only once") {
    for (v <- leaseTxSupportedVersions) {
      val firstBalance  = sender.tacBalance(firstAddress)
      val secondBalance = sender.tacBalance(secondAddress)

      val leaseTx   = sender.broadcastLease(firstAcc, PBRecipients.create(secondAcc.toAddress), leasingAmount, minFee, version = v, waitForTx = true)
      val leaseTxId = PBTransactions.vanilla(leaseTx).explicitGet().id().toString

      sender.broadcastLeaseCancel(firstAcc, leaseTxId, minFee, waitForTx = true)

      assertGrpcError(
        sender.broadcastLeaseCancel(firstAcc, leaseTxId, minFee),
        "Reason: Cannot cancel already cancelled lease",
        Code.INVALID_ARGUMENT
      )
      sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular - 2 * minFee
      sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective - 2 * minFee
      sender.tacBalance(secondAddress).regular shouldBe secondBalance.regular
      sender.tacBalance(secondAddress).effective shouldBe secondBalance.effective

      sender.getActiveLeases(secondAddress) shouldBe List.empty
      sender.getActiveLeases(firstAddress) shouldBe List.empty
    }
  }

  test("only sender can cancel lease transaction") {
    for (v <- leaseTxSupportedVersions) {
      val firstBalance  = sender.tacBalance(firstAddress)
      val secondBalance = sender.tacBalance(secondAddress)

      val leaseTx   = sender.broadcastLease(firstAcc, PBRecipients.create(secondAcc.toAddress), leasingAmount, minFee, version = v, waitForTx = true)
      val vanillaTx = PBTransactions.vanilla(leaseTx).explicitGet()
      val leaseTxId = vanillaTx.id().toString
      val height    = sender.getStatus(leaseTxId).height

      assertGrpcError(
        sender.broadcastLeaseCancel(secondAcc, leaseTxId, minFee),
        "LeaseTransaction was leased by other sender",
        Code.INVALID_ARGUMENT
      )
      sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular - minFee
      sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective - minFee - leasingAmount
      sender.tacBalance(secondAddress).regular shouldBe secondBalance.regular
      sender.tacBalance(secondAddress).effective shouldBe secondBalance.effective + leasingAmount

      val response = toResponse(vanillaTx, height)
      sender.getActiveLeases(secondAddress) shouldBe List(response)
      sender.getActiveLeases(firstAddress) shouldBe List(response)

      sender.broadcastLeaseCancel(firstAcc, leaseTxId, minFee, waitForTx = true)
    }
  }

  test("can not make leasing to yourself") {
    for (v <- leaseTxSupportedVersions) {
      val firstBalance = sender.tacBalance(firstAddress)
      assertGrpcError(
        sender.broadcastLease(firstAcc, PBRecipients.create(firstAcc.toAddress), leasingAmount, minFee, v),
        "Transaction to yourself",
        Code.INVALID_ARGUMENT
      )
      sender.tacBalance(firstAddress).regular shouldBe firstBalance.regular
      sender.tacBalance(firstAddress).effective shouldBe firstBalance.effective
      sender.getActiveLeases(firstAddress) shouldBe List.empty
    }
  }

  private def toResponse(tx: Transaction, height: Long): LeaseResponse = {
    val leaseTx   = tx.asInstanceOf[LeaseTransaction]
    val leaseTxId = ByteString.copyFrom(leaseTx.id.value().arr)
    LeaseResponse(
      leaseId = leaseTxId,
      originTransactionId = leaseTxId,
      sender = ByteString.copyFrom(leaseTx.sender.toAddress.bytes),
      recipient = Some(PBRecipients.create(leaseTx.recipient)),
      amount = leaseTx.amount,
      height = height.toInt
    )
  }
}
