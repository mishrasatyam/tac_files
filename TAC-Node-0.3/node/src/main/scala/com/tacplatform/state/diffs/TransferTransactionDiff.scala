package com.tacplatform.state.diffs

import cats.implicits._
import com.tacplatform.account.Address
import com.tacplatform.features.BlockchainFeatures
import com.tacplatform.lang.ValidationError
import com.tacplatform.state._
import com.tacplatform.transaction.Asset.{IssuedAsset, Tac}
import com.tacplatform.transaction.TxValidationError
import com.tacplatform.transaction.TxValidationError.GenericError
import com.tacplatform.transaction.transfer._

import scala.util.Right
import scala.util.control.NonFatal

object TransferTransactionDiff {
  def apply(blockchain: Blockchain, blockTime: Long)(tx: TransferTransaction): Either[ValidationError, Diff] = {
    val sender = Address.fromPublicKey(tx.sender)

    val isSmartAsset = tx.feeAssetId match {
      case Tac => false
      case asset @ IssuedAsset(_) =>
        blockchain
          .assetDescription(asset)
          .flatMap(_.script)
          .isDefined
    }

    for {
      recipient <- blockchain.resolveAlias(tx.recipient)
      _         <- Either.cond(!isSmartAsset, (), GenericError("Smart assets can't participate in TransferTransactions as a fee"))

      _ <- validateOverflow(blockchain, blockchain.height, tx)
      portfolios = (tx.assetId match {
        case Tac =>
          Map(sender -> Portfolio(-tx.amount, LeaseBalance.empty, Map.empty)).combine(
            Map(recipient -> Portfolio(tx.amount, LeaseBalance.empty, Map.empty))
          )
        case asset @ IssuedAsset(_) =>
          Map(sender -> Portfolio(0, LeaseBalance.empty, Map(asset -> -tx.amount))).combine(
            Map(recipient -> Portfolio(0, LeaseBalance.empty, Map(asset -> tx.amount)))
          )
      }).combine(
        tx.feeAssetId match {
          case Tac => Map(sender -> Portfolio(-tx.fee, LeaseBalance.empty, Map.empty))
          case asset @ IssuedAsset(_) =>
            val senderPf = Map(sender -> Portfolio(0, LeaseBalance.empty, Map(asset -> -tx.fee)))
            if (blockchain.height >= Sponsorship.sponsoredFeesSwitchHeight(blockchain)) {
              val sponsorPf = blockchain
                .assetDescription(asset)
                .collect {
                  case desc if desc.sponsorship > 0 =>
                    val feeInTac = Sponsorship.toTac(tx.fee, desc.sponsorship)
                    Map(desc.issuer.toAddress -> Portfolio(-feeInTac, LeaseBalance.empty, Map(asset -> tx.fee)))
                }
                .getOrElse(Map.empty)
              senderPf.combine(sponsorPf)
            } else senderPf
        }
      )
      assetIssued    = tx.assetId.fold(true)(blockchain.assetDescription(_).isDefined)
      feeAssetIssued = tx.feeAssetId.fold(true)(blockchain.assetDescription(_).isDefined)
      _ <- Either.cond(
        blockTime <= blockchain.settings.functionalitySettings.allowUnissuedAssetsUntil || (assetIssued && feeAssetIssued),
        (),
        GenericError(
          s"Unissued assets are not allowed after allowUnissuedAssetsUntil=${blockchain.settings.functionalitySettings.allowUnissuedAssetsUntil}"
        )
      )
    } yield Diff(
      portfolios = portfolios,
      scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)
    )
  }

  private def validateOverflow(blockchain: Blockchain, height: Int, tx: TransferTransaction) =
    if (blockchain.isFeatureActivated(BlockchainFeatures.Ride4DApps, height))
      Right(()) // lets transaction validates itself
    else
      try {
        Math.addExact(tx.fee, tx.amount)
        Right(())
      } catch {
        case NonFatal(_) => Left(TxValidationError.OverflowError)
      }
}
