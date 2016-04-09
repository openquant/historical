package com.larroy.quant.quotedb

import org.specs2.specification.Scope

import java.io.File

trait TempDBScope extends Scope {
  val tmpPath = File.createTempFile("quoteDB_tests_", ".db")
  tmpPath.deleteOnExit()
  val quoteDB = QuoteDB(s"sqlite://$tmpPath")
}