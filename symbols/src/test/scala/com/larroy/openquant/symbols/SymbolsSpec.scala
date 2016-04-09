package com.larroy.openquant.symbols

import org.specs2.mutable._

/**
  * Created by piotr on 16.01.16.
  */
class SymbolsSpec extends Specification {
  "Symbols" should {
    "list stocks from a given market" in {
        val stocks = Symbols.listExchange(Exchange.NYSE)
        stocks must not be empty
    }
  }
}
