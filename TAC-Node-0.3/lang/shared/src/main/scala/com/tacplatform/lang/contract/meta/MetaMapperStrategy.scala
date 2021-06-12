package com.tacplatform.lang.contract.meta

import com.tacplatform.lang.v1.compiler.Types.FINAL
import com.tacplatform.protobuf.dapp.DAppMeta

private[meta] trait MetaMapperStrategy[V <: MetaVersion] {
  def toProto(data: List[List[FINAL]], nameMap: Map[String, String] = Map.empty): Either[String, DAppMeta]
  def fromProto(meta: DAppMeta): Either[String, List[List[FINAL]]]
}
