package com.tacplatform.lang.v1.traits.domain

import com.tacplatform.common.state.ByteStr

case class APair(amountAsset: Option[ByteStr], priceAsset: Option[ByteStr])
