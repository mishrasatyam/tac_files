package com.tacplatform.transaction

import com.google.common.primitives.Ints
import com.tacplatform.TestValues
import com.tacplatform.account.{Address, AddressOrAlias, KeyPair}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils._
import com.tacplatform.it.util.DoubleExt
import com.tacplatform.lang.script.Script
import com.tacplatform.lang.v1.FunctionHeader
import com.tacplatform.lang.v1.compiler.Terms.{EXPR, FUNCTION_CALL}
import com.tacplatform.lang.v1.estimator.v3.ScriptEstimatorV3
import com.tacplatform.state.StringDataEntry
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.assets.{IssueTransaction, ReissueTransaction}
import com.tacplatform.transaction.assets.exchange.{AssetPair, ExchangeTransaction, Order, OrderType}
import com.tacplatform.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.tacplatform.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.tacplatform.transaction.smart.InvokeScriptTransaction.Payment
import com.tacplatform.transaction.smart.script.ScriptCompiler
import com.tacplatform.transaction.transfer.TransferTransaction

object TxHelpers {
  def signer(i: Int): KeyPair  = KeyPair(Ints.toByteArray(i))
  def address(i: Int): Address = signer(i).toAddress

  val defaultSigner: KeyPair  = signer(0)
  val defaultAddress: Address = defaultSigner.toAddress
  val secondSigner: KeyPair   = signer(1)
  val secondAddress: Address  = secondSigner.toAddress

  private[this] var lastTimestamp = System.currentTimeMillis()
  def timestamp: Long = {
    lastTimestamp += 1
    lastTimestamp
  }

  def genesis(address: Address, amount: Long = 100_000_000.tac): GenesisTransaction =
    GenesisTransaction.create(address, amount, timestamp).explicitGet()

  def transfer(from: KeyPair = defaultSigner, to: AddressOrAlias = secondAddress, amount: Long = 1.tac, asset: Asset = Tac, fee: Long = TestValues.fee, version: Byte = TxVersion.V1): TransferTransaction =
    TransferTransaction.selfSigned(version, from, to, asset, amount, Tac, fee, ByteStr.empty, timestamp).explicitGet()

  def issue(amount: Long = 1000, script: Script = null): IssueTransaction =
    IssueTransaction
      .selfSigned(TxVersion.V2, defaultSigner, "test", "", amount, 0, reissuable = true, Option(script), 1.tac, timestamp)
      .explicitGet()

  def reissue(asset: IssuedAsset, amount: Long = 1000): ReissueTransaction =
    ReissueTransaction
      .selfSigned(TxVersion.V2, defaultSigner, asset, amount, reissuable = true, TestValues.fee, timestamp)
      .explicitGet()

  def data(account: KeyPair = defaultSigner, key: String = "test", value: String = "test"): DataTransaction =
    DataTransaction.selfSigned(TxVersion.V1, account, Seq(StringDataEntry(key, value)), TestValues.fee * 3, timestamp).explicitGet()

  def orderV3(orderType: OrderType, asset: Asset, feeAsset: Asset): Order = {
    orderV3(orderType, asset, Tac, feeAsset)
  }

  def order(orderType: OrderType, asset: Asset): Order =
    orderV3(orderType, asset, Tac)

  def orderV3(orderType: OrderType, amountAsset: Asset, priceAsset: Asset, feeAsset: Asset): Order = {
    Order.selfSigned(
      TxVersion.V3,
      defaultSigner,
      defaultSigner.publicKey,
      AssetPair(amountAsset, priceAsset),
      orderType,
      1L,
      1L,
      timestamp,
      timestamp + 100000,
      1L,
      feeAsset
    )
  }

  def exchange(order1: Order, order2: Order): ExchangeTransaction = {
    ExchangeTransaction
      .signed(
        TxVersion.V2,
        defaultSigner.privateKey,
        order1,
        order2,
        order1.amount,
        order1.price,
        order1.matcherFee,
        order2.matcherFee,
        TestValues.fee,
        timestamp
      )
      .explicitGet()
  }

  def script(scriptText: String): Script = {
    val (script, _) = ScriptCompiler.compile(scriptText, ScriptEstimatorV3).explicitGet()
    script
  }

  def setScript(acc: KeyPair, script: Script): SetScriptTransaction = {
    SetScriptTransaction.selfSigned(TxVersion.V1, acc, Some(script), TestValues.fee, timestamp).explicitGet()
  }

  def invoke(
      dApp: AddressOrAlias,
      func: String,
      args: Seq[EXPR] = Nil,
      payments: Seq[Payment] = Nil,
      fee: Long = TestValues.fee,
      feeAssetId: Asset = Tac
  ): InvokeScriptTransaction = {
    val fc = FUNCTION_CALL(FunctionHeader.User(func), args.toList)
    InvokeScriptTransaction.selfSigned(TxVersion.V1, defaultSigner, dApp, Some(fc), payments, fee, feeAssetId, timestamp).explicitGet()
  }

  def lease(recipient: AddressOrAlias = secondAddress, amount: TxAmount = 10.tac): LeaseTransaction = {
    LeaseTransaction.selfSigned(TxVersion.V2, defaultSigner, recipient, amount, TestValues.fee, timestamp).explicitGet()
  }

  def leaseCancel(leaseId: ByteStr): LeaseCancelTransaction = {
    LeaseCancelTransaction.selfSigned(TxVersion.V2, defaultSigner, leaseId, TestValues.fee, timestamp).explicitGet()
  }
}
