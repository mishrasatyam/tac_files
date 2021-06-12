package com.tacplatform.lang

import com.tacplatform.lang.v1.BaseGlobal

package object hacks {
  private[lang] val Global: BaseGlobal = com.tacplatform.lang.Global // Hack for IDEA
}
