package com.tacplatform.network

import com.tacplatform.common.state.ByteStr
import com.tacplatform.lang.ValidationError
import com.tacplatform.transaction.TxValidationError.GenericError

class InMemoryInvalidBlockStorage extends InvalidBlockStorage {

  var s: Set[ByteStr] = Set.empty[ByteStr]

  override def add(blockId: ByteStr, validationError: ValidationError): Unit = s += blockId

  override def find(blockId: ByteStr): Option[ValidationError] = {
    if (s.contains(blockId)) Some(GenericError("Unknown")) else None
  }

}
