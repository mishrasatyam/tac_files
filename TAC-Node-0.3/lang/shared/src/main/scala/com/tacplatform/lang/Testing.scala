package com.tacplatform.lang
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import com.tacplatform.lang.v1.compiler.Terms._

import scala.util.{Left, Right}

object Testing {

  def evaluated(i: Any): Either[String, EVALUATED] = i match {
    case s: String        => CONST_STRING(s)
    case s: Long          => Right(CONST_LONG(s))
    case s: Int           => Right(CONST_LONG(s))
    case s: ByteStr       => CONST_BYTESTR(s)
    case s: CaseObj       => Right(s)
    case s: Boolean       => Right(CONST_BOOLEAN(s))
    case a: Seq[_]        => ARR(a.map(x => evaluated(x).explicitGet()).toIndexedSeq, false)
    case _                => Left("Bad Assert: unexprected type")
  }
}
