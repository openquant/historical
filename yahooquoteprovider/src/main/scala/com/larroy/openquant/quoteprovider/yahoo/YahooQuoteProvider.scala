package com.larroy.openquant.quoteprovider.yahoo

import java.time.ZonedDateTime
import java.util.{Calendar, Date, TimeZone}

import akka.actor.ActorSystem
import com.larroy.openquant.quoteprovider.QuoteProvider
import com.larroy.quant.common._
import com.larroy.quant.common.utils._
import openquant.yahoofinance.{Quote, YahooFinance, Resolution ⇒ YResolution}

import scala.concurrent.{ExecutionContext, Future}

/**
  * [[QuoteProvider]] adapter for Yahoo Finance
  */
class YahooQuoteProvider(implicit val ec: ExecutionContext) extends QuoteProvider with Logging {
  implicit val actorSystem = ActorSystem("YahooQuoteProvider")
  val yahooFinance = new YahooFinance()
  val Source = "Yahoo Finance"

  override def connected = true

  override def close(): Unit = {}

  override def quotes(
    contract: Contract,
    startDate: ZonedDateTime,
    endDate: ZonedDateTime,
    resolution: Resolution.Enum): Future[IndexedSeq[Bar]] = {

    val start = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    start.setTime(Date.from(startDate.toInstant))

    val end = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    end.setTime(Date.from(endDate.toInstant))

    // avoid any IO when there's no data
    val numPeriods = numPeriodsBetween(startDate, endDate, resolution)
    if (numPeriods == 0) {
      log.debug(s"YahooQuoteProvider, 0 bars between [${startDate}, ${endDate}] @ $resolution")
      Future.successful(IndexedSeq.empty[Bar])
    } else {
      val futureQuotes = yahooFinance.quotes(contract.symbol, startDate, endDate)
      val res = futureQuotes.map { quotes ⇒
        quotes.map(quoteToBar)
      }
      res
    }
  }

  def quoteToBar(quote: Quote): Bar = {
    Bar(
      quote.date.toInstant.toEpochMilli,
      quote.open.toDouble,
      quote.close.toDouble,
      quote.high.toDouble,
      quote.low.toDouble,
      quote.adjClose.toDouble,
      quote.volume.toDouble,
      Source
    )
  }

  override def available(contract: Contract, resolution: Resolution.Enum): Future[String] = {
    yahooFinance.fundamentals(contract.symbol).flatMap { xs ⇒
      val fundamentals = xs.head
      fundamentals.looksValid match {
        case true ⇒ Future.successful(fundamentals.name)
        case false ⇒ Future.failed(new RuntimeException("No such contract"))
      }
    }
  }

  private def toYahooInterval(x: Resolution.Enum): YResolution.Enum = x match {
    case Resolution._1_day ⇒ YResolution.Day
    case Resolution._1_week ⇒ YResolution.Week
  }
}

object YahooQuoteProvider extends Logging {
  def apply(): YahooQuoteProvider = {
    import scala.concurrent.ExecutionContext.Implicits.global
    log.warn("Using scala.concurrent.ExecutionContext.Implicits.global as executor")
    new YahooQuoteProvider()
  }

  def apply(implicit ec: ExecutionContext) = {
    new YahooQuoteProvider()
  }
}
