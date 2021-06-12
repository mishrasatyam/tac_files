package com.tacplatform.transaction.validation.impl

import cats.data.Validated
import com.tacplatform.account.Alias
import com.tacplatform.transaction.CreateAliasTransaction
import com.tacplatform.transaction.validation.{TxValidator, ValidatedV}

object CreateAliasTxValidator extends TxValidator[CreateAliasTransaction] {
  override def validate(tx: CreateAliasTransaction): ValidatedV[CreateAliasTransaction] = {
    import tx._
    V.seq(tx)(
      V.fee(fee),
      Validated.fromEither(Alias.createWithChainId(aliasName, chainId)).toValidatedNel.map((_: Alias) => tx)
    )
  }
}
