package com.larroy.openquant.quoteprovider

import com.larroy.quant.common.Logging

import scala.concurrent.ExecutionContext
import scalaz.\/

/**
  * Boilerplate to allow runtime loading of [[QuoteProvider]] and decouple actual implementations from these sources
  */
object QuoteProviderFactoryLoader extends Logging {
  def quoteProviderFactoryClassName(provider: String): String = provider match {
    case s if s matches "(?i)yahoo" ⇒
      "com.larroy.openquant.quoteprovider.yahoo.YahooQuoteProviderFactory"

    case s if ((s matches "(?i)ib") || (s matches "(?i)InteractiveBrokers")) ⇒
      // FIXME
      "com.larroy.openquant.quoteprovider.ib.IBQuoteProviderFactory"
  }

  /**
    * @param provider full classpaht of the desired [[QuoteProviderFactory]]
    * @return A [[QuoteProviderFactory]] if the class is available
    */
  def apply(provider: String): Throwable \/ QuoteProviderFactory = \/.fromTryCatchNonFatal {
    val className = quoteProviderFactoryClassName(provider)
    val clazz = Class.forName(className)
    clazz.newInstance().asInstanceOf[QuoteProviderFactory]
  }

  /*
  def apply(provider: String, ec: ExecutionContext): Throwable \/ QuoteProviderFactory = \/.fromTryCatchNonFatal {
    val className = quoteProviderFactoryClassName(provider)
    val clazz = Class.forName(className)
    //clazz.getDeclaredConstructor(classOf[ExecutionContext]).newInstance(ec).asInstanceOf[QuoteProviderFactory]
  }
  */
}
