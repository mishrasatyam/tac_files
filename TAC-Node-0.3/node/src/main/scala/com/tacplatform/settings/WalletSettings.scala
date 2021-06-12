package com.tacplatform.settings

import java.io.File

import com.tacplatform.common.state.ByteStr

case class WalletSettings(file: Option[File], password: Option[String], seed: Option[ByteStr])
