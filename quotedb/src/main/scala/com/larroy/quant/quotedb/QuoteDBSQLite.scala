package com.larroy.quant.quotedb

import java.time.{Instant, ZoneId, ZonedDateTime}

import com.larroy.quant.common._
import com.larroy.quant.common.utils._
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._

import scalaz.Scalaz._
import scalaz.{-\/, \/, \/-}

/**
  * An implementation of QuoteDB using SQLite
 *
  * @param dbPath path of the SQLite database
  */
class QuoteDBSQLite(dbPath: String) extends QuoteDB {
  Class.forName("org.sqlite.JDBC")
  private val log: Logger = LoggerFactory.getLogger(this.getClass)

  ConnectionPool.add(dbPath, s"jdbc:sqlite:$dbPath", "", "")

  initialize()

  def commit[A](name: scala.Any)(execution: scala.Function1[scalikejdbc.DBSession, A]): A = {
    using(ConnectionPool.borrow(name)) { conn => DB(conn) autoCommit execution }
  }

  def readOnly[A](name: scala.Any)(execution: scala.Function1[scalikejdbc.DBSession, A]): A = {
    using(ConnectionPool.borrow(name)) { conn => DB(conn).autoClose(true).readOnly(execution) }
  }


  private def createQuotesTable(): Unit = {
    commit(dbPath) { session ⇒
      session.update(
        s"""
           |CREATE TABLE IF NOT EXISTS quotes  (
           | symbol string not null,
           | contractType string not null,
           | exchange string not null,
           | currency string not null,
           | expiry string not null default "", /* date in ISO format http://joda-time.sourceforge.net/apidocs/org/joda/time/format/ISODateTimeFormat.html */
           | resolution int not null,
           | date bigint not null,
           | open real,
           | close real,
           | high real,
           | low real,
           | adjclose real,
           | volume real,
           | source string,
           | PRIMARY KEY (symbol, contractType, exchange, currency, expiry, resolution, date)
           |)
         """.stripMargin
      )
    }
  }

  private def createSubscriptionsTable(): Unit = {
    commit(dbPath) { session ⇒
      session.update(
        s"""
           |CREATE TABLE IF NOT EXISTS subscriptions (
           | symbol string not null,
           | contractType string not null,
           | exchange string not null,
           | currency string not null,
           | expiry string not null default "",
           | resolution int not null,
           | source string not null default "",
           | lastUpdateSuccess string not null default "",
           | lastUpdateAttempt string not null default "",
           | lastUpdateStatus string not null default "",
           | PRIMARY KEY (symbol, contractType, exchange, currency, expiry, resolution)
           |)
         """.stripMargin
      )
    }
  }

  override def subscribe(contract: Contract, resolution: Resolution.Enum, source: String): Throwable \/ Unit = {
    \/.fromTryCatchNonFatal(commit(dbPath) { session ⇒
      session.update("INSERT INTO subscriptions (symbol, contractType, exchange, currency, expiry, resolution, source) VALUES (?,?,?, ?,?,?, ?)",
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id,
        source
      )
    }).map(x ⇒ Unit)
  }

  override def unsubscribe(contract: Contract, resolution: Resolution.Enum): Throwable \/ Unit = \/.fromTryCatchNonFatal {
    commit(dbPath) { session ⇒
      session.update("DELETE FROM subscriptions WHERE symbol = ? AND contractType = ? AND exchange = ? AND currency = ? AND expiry = ? AND resolution = ?",
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id
      )
    }
  } match {
    case \/-(count) if count > 0 ⇒
      \/-(Unit)

    case \/-(count) if count == 0 ⇒
      -\/(Error(s"The subscription for contract ${contract} was not found"))

    case -\/(e) ⇒
      -\/(e)
  }


  override def subscriptions(): Seq[Subscription] = {
    commit(dbPath) { session ⇒
      val res = session.list(
        """
          | SELECT
          |   symbol,
          |   contractType,
          |   exchange,
          |   currency,
          |   expiry,
          |   resolution,
          |   lastUpdateSuccess,
          |   lastUpdateAttempt,
          |   lastUpdateStatus,
          |   source
          | FROM subscriptions
          | ORDER BY symbol, exchange
        """.stripMargin
      ) { rs ⇒
        Subscription(
          Contract(rs.string(1), rs.string(2), rs.string(3), rs.string(4), fromISO(rs.string(5))),
          Resolution(rs.int(6)),
          fromISO(rs.string(7)),
          fromISO(rs.string(8)),
          \/.fromTryCatchNonFatal(UpdateStatus.withName(rs.string(9))).toOption,
          rs.string(10)
        )
      }
      res
    }
  }

  override def subscription(contract: Contract, resolution: Resolution.Enum): Throwable \/ Subscription = {
    val maybeSubscription = commit(dbPath) { session ⇒
      session.single(
        """
          | SELECT
          |  symbol,
          |  contractType,
          |  exchange,
          |  currency,
          |  expiry,
          |  resolution,
          |  lastUpdateSuccess,
          |  lastUpdateAttempt,
          |  lastUpdateStatus,
          |  source
          | FROM subscriptions
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
          | ORDER BY symbol, exchange
        """.stripMargin,
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id
      ) { rs ⇒
        Subscription(
          Contract(rs.string(1), rs.string(2), rs.string(3), rs.string(4), fromISO(rs.string(5))),
          Resolution(rs.int(6)),
          fromISO(rs.string(7)),
          fromISO(rs.string(8)),
          \/.fromTryCatchNonFatal(UpdateStatus.withName(rs.string(9))).toOption,
          rs.string(10)
        )
      }
    }
    maybeSubscription.toRightDisjunction[Throwable](Error(s"No subscription for contract ${contract} with ${resolution}"))
  }

  override def initialize(): Unit = {
    createQuotesTable()
    createSubscriptionsTable()
  }

  override def availableResolutions(contract: Contract): Set[Resolution.Enum] = {
    commit(dbPath) { session ⇒
      val res = session.list(
        """
          | SELECT DISTINCT(resolution)
          | FROM quotes
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          | ORDER BY resolution
        """.stripMargin,
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse("")
      ) { rs ⇒ Resolution(rs.int(1)) }.toSet
      res
    }
  }

  override def count(contract: Contract, resolution: Resolution.Enum): Long = {
    commit(dbPath) { session ⇒
      val res = session.single(
        """
          | SELECT COUNT(*)
          | FROM quotes
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
        """.stripMargin,
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id
      ) { rs ⇒ rs.int(1) }.getOrElse(0)
      res
    }
  }

  override def firstDate(contract: Contract, resolution: Resolution.Enum): Throwable \/ ZonedDateTime = {
    commit(dbPath) { session ⇒
      val maybeEpoch = session.single(
        """
          | SELECT MIN(date)
          | FROM quotes
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
        """.stripMargin,
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id
      ) { rs ⇒ rs.int(1) }
      maybeEpoch.toRightDisjunction(Error(s"No quotes found for contract ${contract} with ${resolution}")).map { ms ⇒
          ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault)
      }
    }
  }

  /**
   * @param contract
   * @return an optional Date if the contract exists
   */
  override def lastDate(contract: Contract, resolution: Resolution.Enum): Throwable \/ ZonedDateTime = {
    commit(dbPath) { session ⇒
      val maybeEpoch = session.single(
        """
          | SELECT MAX(date)
          | FROM quotes
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
        """.stripMargin,
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id
      ) { rs ⇒ rs.long(1) }
      maybeEpoch.toRightDisjunction(Error(s"No quotes found for contract ${contract} with ${resolution}")).map { ms ⇒
          ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault)
      }
    }
  }

  /**
   * @param contract
   * @param startDate
   * @param endDate
   * @return
   */
  def quotes(
    contract: Contract,
    resolution: Resolution.Enum = Resolution._1_day,
    startDate: Option[ZonedDateTime] = None,
    endDate: Option[ZonedDateTime] = None): Throwable \/ scala.collection.immutable.IndexedSeq[Bar] = \/.fromTryCatchNonFatal {

    val bars: Vector[Bar] = commit(dbPath) { session ⇒
      session.collection[Bar, Vector](
        """
          | SELECT
          |   date,
          |   open,
          |   close,
          |   high,
          |   low,
          |   adjclose,
          |   volume,
          |   source
          | FROM quotes
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
          |   AND date >= ?
          |   AND date <= ?
          | ORDER BY
          |   date
          | ASC
        """.stripMargin,
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id,
        startDate.map(toEpoch).getOrElse(0L),
        endDate.map(toEpoch).getOrElse(Long.MaxValue)
      ) { rs ⇒
        new Bar(rs.long(1), rs.double(2), rs.double(3), rs.double(4), rs.double(5), rs.double(6), rs.double(7), rs.string(8))
      }
    }
    bars
  }

  override def upsert(contract: Contract, bars: Iterable[Bar], resolution: Resolution.Enum): Throwable \/ Unit = \/.fromTryCatchNonFatal {
    /*
    Delete these bars if they exist, then insert them
     */
    val delete: Seq[Seq[Any]] = bars.map { bar ⇒
      Seq(
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id,
        bar.date
      )
    }.toSeq
    val insert: Seq[Seq[Any]] = bars.map { bar ⇒
      Seq(
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id,
        bar.date,
        bar.open,
        bar.close,
        bar.high,
        bar.low,
        bar.adjClose,
        bar.volume,
        bar.source
      )
    }.toSeq
    commit(dbPath) { session ⇒
      log.debug(s"QuoteDBSQLite.upsert, delete previous quotes")

      session.execute("PRAGMA synchronous = 0")
      session.execute("PRAGMA journal_mode = OFF")
      session.execute("PRAGMA cache_size = 64000")


      utils.time {
        session.batch(
          """
          | DELETE FROM quotes
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
          |   AND date = ?
        """.stripMargin, delete: _*)
      }

      log.debug(s"QuoteDBSQLite.upsert, insert new (${insert.size}) quotes")
      utils.time {
        session.batch(
          """
          | INSERT INTO quotes
          |   (symbol,
          |    contractType,
          |    exchange,
          |    currency,
          |    expiry,
          |    resolution,
          |    date,
          |    open,
          |    close,
          |    high,
          |    low,
          |    adjclose,
          |    volume,
          |    source
          |    )
          |  VALUES
          |    (?,?,?, ?,?,?, ?,?,?, ?,?,?, ?,?)
        """.stripMargin, insert: _*)
      }

      writeLastUpdateAttempt(contract, resolution, UpdateStatus.Success)
    }
  }

  override def writeLastUpdateAttempt(
    contract: Contract,
    resolution: Resolution.Enum,
    updateStatus: UpdateStatus.Value,
    date: ZonedDateTime): Throwable \/ Unit = \/.fromTryCatchNonFatal {

    commit(dbPath) { session ⇒
      session.update(
        """
          | UPDATE subscriptions SET
          |   lastUpdateAttempt = ?,
          |   lastUpdateStatus = ?
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
        """.stripMargin,
        toISO(date),
        updateStatus.toString,
        contract.symbol,
        contract.contractType,
        contract.exchange,
        contract.currency,
        contract.expiry.map(toISO).getOrElse(""),
        resolution.id
      )

      if (updateStatus == UpdateStatus.Success) {
        val res = session.update(
          """
          | UPDATE subscriptions SET
          |   lastUpdateSuccess = ?
          | WHERE
          |   symbol = ?
          |   AND contractType = ?
          |   AND exchange = ?
          |   AND currency = ?
          |   AND expiry = ?
          |   AND resolution = ?
        """.stripMargin,
          toISO(date),
          contract.symbol,
          contract.contractType,
          contract.exchange,
          contract.currency,
          contract.expiry.map(toISO).getOrElse(""),
          resolution.id
        )
      }
    }
  }
}
