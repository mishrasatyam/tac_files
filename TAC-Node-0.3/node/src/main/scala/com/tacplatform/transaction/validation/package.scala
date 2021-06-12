package com.tacplatform.transaction

import cats.data.ValidatedNel
import com.tacplatform.lang.ValidationError

package object validation {
  type ValidatedV[A] = ValidatedNel[ValidationError, A]
  type ValidatedNV   = ValidatedV[Unit]
}
