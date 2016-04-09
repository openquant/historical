package com.larroy.quant.quotedb

import java.time.ZonedDateTime

import com.larroy.quant.common._
import com.typesafe.config.ConfigFactory

import scalaz.\/


/**
  * @author piotr 03.05.15
  */
trait QuoteDB {

  def initialize(): Unit

  /**
    * Activate subscription for a contract at a given resolution
    * @param resolution granularity of the data
    * @param source     name of the data source
    * @return Either [[\/]] an error or Unit if successful
    */
  def subscribe(contract: Contract, resolution: Resolution.Enum, source: String = ""): Throwable \/ Unit

  def unsubscribe(contract: Contract, resolution: Resolution.Enum): Throwable \/ Unit

  /**
    * @return all subscriptions
    */
  def subscriptions(): Seq[Subscription]

  /**
    * Get the [[Subscription]] information about a contract if exists
    *
    * @return
    */
  def subscription(contract: Contract, resolution: Resolution.Enum): Throwable \/ Subscription

  /**
    * Last date the contract was updated
    *
    * @return an optional Date if the contract exists
    */
  def lastDate(contract: Contract, resolution: Resolution.Enum = Resolution._1_day): Throwable \/ ZonedDateTime

  /**
    * Date of first bar available for the contract
    *
    * @return
    */
  def firstDate(contract: Contract, resolution: Resolution.Enum = Resolution._1_day): Throwable \/ ZonedDateTime

  /**
    * Get quotes for a contract, optionally in the range given by startDate and endDate
    */
  def quotes(
    contract: Contract,
    resolution: Resolution.Enum = Resolution._1_day,
    startDate: Option[ZonedDateTime] = None,
    endDate: Option[ZonedDateTime] = None): Throwable \/ scala.collection.immutable.IndexedSeq[Bar]

  /**
    * Update or insert the given quotes for a contract
    */
  def upsert(contract: Contract, quotes: Iterable[Bar], resolution: Resolution.Enum = Resolution._1_day): Throwable \/ Unit

  /**
    * Write in the database the given date as the last time when an update was attempted
    */
  def writeLastUpdateAttempt(
    contract: Contract,
    resolution: Resolution.Enum,
    updateStatus: UpdateStatus.Enum,
    date: ZonedDateTime = ZonedDateTime.now()): Throwable \/ Unit

  /**
    * @return Number of quotes for a contract
    */
  def count(contract: Contract, resolution: Resolution.Enum = Resolution._1_day): Long

  /**
    * @return set of resolutions which we have quotes for
    */
  def availableResolutions(contract: Contract): Set[Resolution.Enum]

}

object QuoteDB {
  private val sqlite = "sqlite://(.*?)".r

  def apply(url: String): QuoteDB = url match {
    case "" ⇒ apply()
    case sqlite(path) ⇒ new QuoteDBSQLite(path)
    case _ ⇒ throw new RuntimeException(s"No implementation of QuoteDB with the provided url '$url' found")
  }

  def apply(): QuoteDB = {
    import net.ceedubs.ficus.Ficus._
    apply(ConfigFactory.load().getConfig("quoteDB").as[String]("quoteDBUrl"))
  }
}

