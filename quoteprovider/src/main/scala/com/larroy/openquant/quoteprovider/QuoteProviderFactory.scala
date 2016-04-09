package com.larroy.openquant.quoteprovider

import scala.concurrent.ExecutionContext

trait QuoteProviderFactory {
  def apply(implicit ec: ExecutionContext): QuoteProvider
}
