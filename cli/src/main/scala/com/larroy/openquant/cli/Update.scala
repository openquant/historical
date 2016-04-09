package com.larroy.openquant.cli

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.larroy.openquant.quoteprovider.QuoteProviderFactoryLoader
import com.larroy.quant.common._
import com.larroy.quant.common.utils.{ThrottlingExecutionContext, toPrintable}
import com.larroy.openquant.quoteprovider.QuoteProvider
import com.larroy.quant.quotedb.{QuoteDB, UpdateStatus}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scalaz.{-\/, \/, \/-}
import scala.concurrent.ExecutionContext.Implicits.global

class Update(quoteProvider: QuoteProvider, quoteDB: QuoteDB) {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)

  def updateSymbol(contract: Contract, start: ZonedDateTime, end: ZonedDateTime, resolution: Resolution.Enum): Future[Unit] = {
    quoteProvider.quotes(contract, start, end, resolution).map { bars ⇒
      quoteDB.upsert(contract, bars, resolution) match {
        case \/-(_) ⇒
        case -\/(e) ⇒ throw e
      }
    }.recover {
      case e ⇒
        quoteDB.writeLastUpdateAttempt(contract, resolution, UpdateStatus.Failure)
        throw e
    }
  }

  def update(contract: Contract, resolution: Resolution.Enum): Future[Unit] = {
    quoteDB.lastDate(contract, resolution) match {
      case \/-(last) ⇒
        updateSymbol(contract, last, Update.defaultEndDate, resolution)
      case -\/(e) ⇒
        Future.failed(e)
    }
  }

  def updateAll(): Throwable \/ Unit = {
    // FIXME
    implicit val ec = ThrottlingExecutionContext(sys.runtime.availableProcessors(), 32)
    val subscriptions = quoteDB.subscriptions()
    if (subscriptions.isEmpty) {
      log.info("No subscriptions found")
      return \/-(Unit)
    }
    subscriptions.foreach { subscription ⇒
      val startDate = quoteDB.lastDate(subscription.contract).getOrElse(Update.defaultStartDate)
      val endDate: ZonedDateTime = Update.defaultEndDate
      val df = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")
      log.info(s"updateSymbol ${subscription.contract} [${toPrintable(startDate)}, ${toPrintable(endDate)}] ${subscription.resolution}")
      updateSymbol(subscription.contract, startDate, endDate, subscription.resolution)
    }
    \/-(Unit)
  }
}

object Update {
  val cfg = ConfigFactory.load().getConfig("cli")

  def defaultStartDate = cfg.as[ZonedDateTime]("defaultStartDate")

  def defaultEndDate = ZonedDateTime.now().minusMinutes(5)

  def apply(options: Options): Throwable \/ Unit = {
    implicit val ec = ThrottlingExecutionContext(sys.runtime.availableProcessors(), 32)
    // FIXME return
    val quoteDB = QuoteDB(options.quoteDBUrl)
    val quoteProvider = QuoteProviderFactoryLoader(options.source).map { x ⇒ x(ec) }
    quoteProvider.map { quoteProvider ⇒
      val update = new Update(quoteProvider, quoteDB)
      if (options.symbol.isEmpty)
        update.updateAll()
      else {
        val sym = Contract(options.symbol, options.contractType, options.exchange, options.currency, options.maybeExpiry)
        val startDate = quoteDB.lastDate(sym, options.resolution).getOrElse(options.maybeStartDate.getOrElse(defaultEndDate))
        val endDate = options.maybeEndDate.getOrElse(defaultEndDate)
        Await.result(update.updateSymbol(
          sym,
          startDate,
          endDate,
          options.resolution
        ), Duration.Inf)
      }
    }
    \/-(Unit)
  }
}
