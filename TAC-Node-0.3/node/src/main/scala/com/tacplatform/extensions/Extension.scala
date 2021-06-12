package com.tacplatform.extensions

import scala.concurrent.Future

trait Extension {
  def start(): Unit
  def shutdown(): Future[Unit]
}
