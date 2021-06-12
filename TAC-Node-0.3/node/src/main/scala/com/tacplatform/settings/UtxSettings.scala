package com.tacplatform.settings

case class UtxSettings(
    maxSize: Int,
    maxBytesSize: Long,
    maxScriptedSize: Int,
    blacklistSenderAddresses: Set[String],
    allowBlacklistedTransferTo: Set[String],
    fastLaneAddresses: Set[String],
    allowTransactionsFromSmartAccounts: Boolean,
    allowSkipChecks: Boolean
)
