package com.tacplatform.it.sync.grpc

import com.google.protobuf.ByteString
import com.tacplatform.account.KeyPair
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.it.sync.minFee
import com.tacplatform.protobuf.transaction.Recipient

class GeneratingBalanceSuite extends GrpcBaseTransactionSuite {

  test("Generating balance should be correct") {
    val amount = 1000000000L

    val senderAddress = ByteString.copyFrom(sender.keyPair.toAddress.bytes)

    val recipient        = KeyPair("recipient".getBytes)
    val recipientAddress = ByteString.copyFrom(recipient.toAddress.bytes)

    val initialBalance = sender.tacBalance(senderAddress)

    sender.broadcastTransfer(sender.keyPair, Recipient().withPublicKeyHash(recipientAddress), amount, minFee, 2, waitForTx = true)

    val afterTransferBalance = sender.tacBalance(senderAddress)

    sender.broadcastTransfer(recipient, Recipient().withPublicKeyHash(senderAddress), amount - minFee, minFee, 2, waitForTx = true)

    val finalBalance = sender.tacBalance(senderAddress)

    assert(initialBalance.generating <= initialBalance.effective, "initial incorrect")
    assert(afterTransferBalance.generating <= afterTransferBalance.effective, "after transfer incorrect")
    assert(finalBalance.generating <= finalBalance.effective, "final incorrect")
  }
}
