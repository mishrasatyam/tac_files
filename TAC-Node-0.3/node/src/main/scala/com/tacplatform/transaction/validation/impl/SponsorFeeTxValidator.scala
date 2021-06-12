package com.tacplatform.transaction.validation.impl

import cats.implicits._
import com.tacplatform.transaction.TxAmount
import com.tacplatform.transaction.TxValidationError.NegativeMinFee
import com.tacplatform.transaction.assets.SponsorFeeTransaction
import com.tacplatform.transaction.validation.{TxValidator, ValidatedV}

object SponsorFeeTxValidator extends TxValidator[SponsorFeeTransaction] {
  override def validate(tx: SponsorFeeTransaction): ValidatedV[SponsorFeeTransaction] = {
    import tx._
    V.seq(tx)(
      checkMinSponsoredAssetFee(minSponsoredAssetFee).toValidatedNel,
      V.fee(fee)
    )
  }

  def checkMinSponsoredAssetFee(minSponsoredAssetFee: Option[TxAmount]): Either[NegativeMinFee, Unit] =
    Either.cond(minSponsoredAssetFee.forall(_ > 0), (), NegativeMinFee(minSponsoredAssetFee.get, "asset"))
}
