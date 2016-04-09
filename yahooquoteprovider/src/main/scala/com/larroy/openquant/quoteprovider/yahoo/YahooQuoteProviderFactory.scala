package com.larroy.openquant.quoteprovider.yahoo

import com.larroy.openquant.quoteprovider.{QuoteProvider, QuoteProviderFactory}

import scala.concurrent.ExecutionContext

class YahooQuoteProviderFactory extends QuoteProviderFactory {
  def apply(implicit ec: ExecutionContext): QuoteProvider ={
    YahooQuoteProvider(ec)
  }
}
