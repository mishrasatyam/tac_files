package com.tacplatform.it.sync.grpc

import com.tacplatform.account.AddressScheme
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.it.NTPTime
import com.tacplatform.it.api.SyncGrpcApi._
import com.tacplatform.it.sync.{aliasTxSupportedVersions, minFee, transferAmount}
import com.tacplatform.it.util._
import com.tacplatform.protobuf.transaction.{PBRecipients, Recipient}
import io.grpc.Status.Code
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.Random

class CreateAliasTransactionGrpcSuite extends GrpcBaseTransactionSuite with NTPTime with TableDrivenPropertyChecks {

  val (aliasCreator, aliasCreatorAddr) = (firstAcc, firstAddress)
  test("Able to send money to an alias") {
    for (v <- aliasTxSupportedVersions) {
      val alias             = randomAlias()
      val creatorBalance    = sender.tacBalance(aliasCreatorAddr).available
      val creatorEffBalance = sender.tacBalance(aliasCreatorAddr).effective

      sender.broadcastCreateAlias(aliasCreator, alias, minFee, version = v, waitForTx = true)

      sender.tacBalance(aliasCreatorAddr).available shouldBe creatorBalance - minFee
      sender.tacBalance(aliasCreatorAddr).effective shouldBe creatorEffBalance - minFee

      sender.resolveAlias(alias) shouldBe PBRecipients.toAddress(aliasCreatorAddr.toByteArray, AddressScheme.current.chainId).explicitGet()

      sender.broadcastTransfer(aliasCreator, Recipient().withAlias(alias), transferAmount, minFee, waitForTx = true)

      sender.tacBalance(aliasCreatorAddr).available shouldBe creatorBalance - 2 * minFee
      sender.tacBalance(aliasCreatorAddr).effective shouldBe creatorEffBalance - 2 * minFee
    }
  }

  test("Not able to create same aliases to same address") {
    for (v <- aliasTxSupportedVersions) {
      val alias             = randomAlias()
      val creatorBalance    = sender.tacBalance(aliasCreatorAddr).available
      val creatorEffBalance = sender.tacBalance(aliasCreatorAddr).effective

      sender.broadcastCreateAlias(aliasCreator, alias, minFee, version = v, waitForTx = true)
      sender.tacBalance(aliasCreatorAddr).available shouldBe creatorBalance - minFee
      sender.tacBalance(aliasCreatorAddr).effective shouldBe creatorEffBalance - minFee

      assertGrpcError(sender.broadcastCreateAlias(aliasCreator, alias, minFee, version = v), "Alias already claimed", Code.INVALID_ARGUMENT)

      sender.tacBalance(aliasCreatorAddr).available shouldBe creatorBalance - minFee
      sender.tacBalance(aliasCreatorAddr).effective shouldBe creatorEffBalance - minFee
    }
  }

  test("Not able to create aliases to other addresses") {
    for (v <- aliasTxSupportedVersions) {
      val alias            = randomAlias()
      val secondBalance    = sender.tacBalance(secondAddress).available
      val secondEffBalance = sender.tacBalance(secondAddress).effective

      sender.broadcastCreateAlias(aliasCreator, alias, minFee, version = v, waitForTx = true)
      assertGrpcError(sender.broadcastCreateAlias(secondAcc, alias, minFee, version = v), "Alias already claimed", Code.INVALID_ARGUMENT)

      sender.tacBalance(secondAddress).available shouldBe secondBalance
      sender.tacBalance(secondAddress).effective shouldBe secondEffBalance
    }
  }

  val aliases_names =
    Table(s"aliasName${randomAlias()}", s"aaaa${randomAlias()}", s"....${randomAlias()}", s"123456789.${randomAlias()}", s"@.@-@_@${randomAlias()}")

  aliases_names.foreach { alias =>
    test(s"create alias named $alias") {
      for (v <- aliasTxSupportedVersions) {
        sender.broadcastCreateAlias(aliasCreator, s"$alias$v", minFee, version = v, waitForTx = true)
        sender.resolveAlias(s"$alias$v") shouldBe PBRecipients
          .toAddress(aliasCreatorAddr.toByteArray, AddressScheme.current.chainId)
          .explicitGet()
      }
    }
  }

  val invalid_aliases_names =
    Table(
      ("aliasName", "message"),
      ("", "Alias '' length should be between 4 and 30"),
      ("abc", "Alias 'abc' length should be between 4 and 30"),
      ("morethen_thirtycharactersinline", "Alias 'morethen_thirtycharactersinline' length should be between 4 and 30"),
      ("~!|#$%^&*()_+=\";:/?><|\\][{}", "Alias should contain only following characters: -.0123456789@_abcdefghijklmnopqrstuvwxyz"),
      ("multilnetest\ntest", "Alias should contain only following characters: -.0123456789@_abcdefghijklmnopqrstuvwxyz"),
      ("UpperCaseAliase", "Alias should contain only following characters: -.0123456789@_abcdefghijklmnopqrstuvwxyz")
    )

  forAll(invalid_aliases_names) { (alias: String, message: String) =>
    test(s"Not able to create alias named $alias") {
      for (v <- aliasTxSupportedVersions) {
        assertGrpcError(sender.broadcastCreateAlias(aliasCreator, alias, minFee, version = v), message, Code.INVALID_ARGUMENT)
      }
    }
  }

  test("Able to lease by alias") {
    for (v <- aliasTxSupportedVersions) {
      val (leaser, leaserAddr) = (thirdAcc, thirdAddress)
      val alias                = randomAlias()

      val aliasCreatorBalance    = sender.tacBalance(aliasCreatorAddr).available
      val aliasCreatorEffBalance = sender.tacBalance(aliasCreatorAddr).effective
      val leaserBalance          = sender.tacBalance(leaserAddr).available
      val leaserEffBalance       = sender.tacBalance(leaserAddr).effective

      sender.broadcastCreateAlias(aliasCreator, alias, minFee, version = v, waitForTx = true)
      val leasingAmount = 1.tac

      sender.broadcastLease(leaser, Recipient().withAlias(alias), leasingAmount, minFee, waitForTx = true)

      sender.tacBalance(aliasCreatorAddr).available shouldBe aliasCreatorBalance - minFee
      sender.tacBalance(aliasCreatorAddr).effective shouldBe aliasCreatorEffBalance + leasingAmount - minFee
      sender.tacBalance(leaserAddr).available shouldBe leaserBalance - leasingAmount - minFee
      sender.tacBalance(leaserAddr).effective shouldBe leaserEffBalance - leasingAmount - minFee
    }
  }

  test("Not able to create alias when insufficient funds") {
    for (v <- aliasTxSupportedVersions) {
      val balance = sender.tacBalance(aliasCreatorAddr).available
      val alias   = randomAlias()
      assertGrpcError(
        sender.broadcastCreateAlias(aliasCreator, alias, balance + minFee, version = v),
        "Accounts balance errors",
        Code.INVALID_ARGUMENT
      )
    }
  }

  private def randomAlias(): String = {
    s"testalias.${Random.alphanumeric.take(9).mkString}".toLowerCase
  }

}
