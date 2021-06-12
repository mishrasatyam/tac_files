package com.tacplatform.state.patch

import com.tacplatform.state.{Blockchain, Diff}

trait DiffPatchFactory {
  def isApplicable(b: Blockchain): Boolean = b.height == this.height
  def height: Int
  def apply(): Diff
}
