package com.tacplatform.transaction
import com.tacplatform.account.PublicKey

trait Authorized {
  val sender: PublicKey
}
