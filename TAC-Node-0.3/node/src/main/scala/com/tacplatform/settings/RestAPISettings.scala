package com.tacplatform.settings

case class RestAPISettings(
    enable: Boolean,
    bindAddress: String,
    port: Int,
    apiKeyHash: String,
    cors: Boolean,
    apiKeyDifferentHost: Boolean,
    transactionsByAddressLimit: Int,
    distributionAddressLimit: Int,
    evaluateScriptComplexityLimit: Int,
    limitedPoolThreads: Int
)
