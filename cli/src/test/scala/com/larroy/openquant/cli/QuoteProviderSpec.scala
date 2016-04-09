package com.larroy.openquant.cli

import com.larroy.openquant.quoteprovider.{QuoteProviderFactory, QuoteProviderFactoryLoader}
import org.specs2.matcher.DisjunctionMatchers
import org.specs2.mutable._

/**
  * Test dynamic loading of QuoteProviders
  */
class QuoteProviderSpec extends Specification with DisjunctionMatchers {
  "Yahoo QuoteProviderFactory should be loaded for provider 'yahoo'" >> {
    val quoteProviderFactory = QuoteProviderFactoryLoader("yahoo")
    quoteProviderFactory should be_\/-[QuoteProviderFactory]
  }
}