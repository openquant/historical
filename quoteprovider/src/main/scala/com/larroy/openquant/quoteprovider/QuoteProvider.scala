package com.larroy.openquant.quoteprovider

import java.io.Closeable
import java.time.ZonedDateTime
import scala.concurrent.Future
import com.larroy.quant.common.{Contract, Resolution, Bar}

trait QuoteProvider extends Closeable {
  def name = "<unknown>"
  def connected: Boolean
  /**
    * @return Either a Throwable on error or a map of seconds since epoch to Bars
    */
  def quotes(
    contract: Contract,
    startDate: ZonedDateTime,
    endDate: ZonedDateTime = ZonedDateTime.now.minusDays(7),
    resolution: Resolution.Enum = Resolution._1_day): Future[IndexedSeq[Bar]]

  /**
    * @param contract
    * @param resolution
    * @return a successful future with maybe contract information if there are quotes for the given contract
    */
  def available(contract: Contract, resolution: Resolution.Enum): Future[String]
}
