package com.tacplatform.state

import com.tacplatform.account.PublicKey
import com.tacplatform.lang.script.Script

case class AccountScriptInfo(
    publicKey: PublicKey,
    script: Script,
    verifierComplexity: Long,
    complexitiesByEstimator: Map[Int, Map[String, Long]] = Map.empty
)
