package com.tacplatform.transaction

trait SigProofsSwitch extends ProvenTransaction { self: VersionedTransaction =>
  def usesLegacySignature: Boolean =
    self.version == Transaction.V1
}
