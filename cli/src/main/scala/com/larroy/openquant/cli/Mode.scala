package com.larroy.openquant.cli

object Mode extends Enumeration {
  type Enum = Value
  val Invalid, Update, Subscribe, SubscribeAll = Value
}