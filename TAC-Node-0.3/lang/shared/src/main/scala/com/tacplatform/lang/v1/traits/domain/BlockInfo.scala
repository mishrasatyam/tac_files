package com.tacplatform.lang.v1.traits.domain
import com.tacplatform.common.state.ByteStr

case class BlockInfo(timestamp: Long,
                     height: Int,
                     baseTarget: Long,
                     generationSignature: ByteStr,
                     generator: ByteStr,
                     generatorPublicKey: ByteStr,
                     vrf: Option[ByteStr])
