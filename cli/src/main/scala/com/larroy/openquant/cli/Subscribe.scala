package com.larroy.openquant.cli

import java.util.Date
import java.util.concurrent.{Executors, Future, ThreadPoolExecutor}

import akka.actor.ActorSystem
import com.larroy.openquant.quoteprovider.QuoteProviderFactoryLoader
import com.larroy.openquant.symbols.{Exchange, Symbols}
import com.larroy.quant.common._
import com.larroy.quant.common.utils.{SameThreadExecutionContext, ThrottlingExecutionContext}
import com.larroy.quant.quotedb._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scalaz.{-\/, \/, \/-}

//import scalaz._
//import Scalaz._
import akka.stream._
import akka.stream.scaladsl._


/**
  * @author piotr 24.05.15
  */
object Subscribe extends Logging {
  def apply(options: Options): Throwable \/ Unit = {
    val quoteDB = QuoteDB(options.quoteDBUrl)
    val quoteProviderFactory = QuoteProviderFactoryLoader(options.source)
    quoteProviderFactory.flatMap { quoteProviderFactory ⇒
      val quoteProvider = quoteProviderFactory(ThrottlingExecutionContext(8, 16))
      val contract = Contract(options.symbol, options.contractType, options.exchange, options.currency, options.maybeExpiry)
      val desc = Await.result(quoteProvider.available(contract, options.resolution), Duration.Inf)
      quoteDB.subscribe(contract, options.resolution, options.source)
    }
  }

  def all(options: Options): Throwable \/ Unit = {
    import DisjunctionValues.convertDisjunctionToDisjunctionated
    implicit val system = ActorSystem("QuickStart")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val quoteProviderDis = for {
      quoteProviderFactory ← QuoteProviderFactoryLoader(options.source)
      quoteProvider = quoteProviderFactory(ec)
    } yield quoteProvider
    if (quoteProviderDis.isLeft)
      return quoteProviderDis.map(_ ⇒ Unit)
    val quoteProvider = quoteProviderDis.value
    val quoteDB = QuoteDB(options.quoteDBUrl)


    val stockList = Symbols.listExchange(Exchange.NYSE)
    val stockSource = Source(stockList)
    val res = stockSource.map { stockInfo ⇒
      log.debug(s"Process: $stockInfo")
      val contract = Contract(stockInfo.ticker, options.contractType, options.exchange, options.currency, options.maybeExpiry)
      if (quoteDB.subscription(contract, options.resolution).isLeft) {
        Await.ready(quoteProvider.available(contract, options.resolution), Duration(10, SECONDS)).value.get match {
          case Success(x) ⇒
            quoteDB.subscribe(contract, options.resolution, options.source) match {
              case -\/(e) ⇒
                log.error("Subscribe error $e")

              case _ ⇒
                log.info(s"Subscribed to ${contract}")
            }
          case Failure(e) ⇒
            log.error(s"contract not available: $e")
        }
      }
      stockInfo
    }
    val r = res.runForeach(x ⇒ log.debug(s"$x"))
    Await.result(r, Duration.Inf)
    log.debug("done")
    \/-(Unit)
  }
}


//stockSource
//implicit val ec = ThrottlingExecutionContext(5, 2)
/*
implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
val quoteProviderDis = for {
  quoteProviderFactory ← QuoteProviderFactoryLoader(options.source)
  quoteProvider = quoteProviderFactory(ec)
} yield quoteProvider

if (quoteProviderDis.isLeft)
  return quoteProviderDis.map(_ ⇒ Unit)

val quoteProvider = quoteProviderDis.toOption.get
val quoteDB = QuoteDB(options.quoteDBUrl)
stockList.foreach { stockInfo ⇒
  val contract = Contract(stockInfo.ticker, options.contractType, options.exchange, options.currency, options.maybeExpiry)
  if (quoteDB.subscription(contract, options.resolution).isLeft) {
    // no subscription found
    Await.result(quoteProvider.available(contract, options.resolution), Duration(10, SECONDS))
      quoteDB.subscribe(contract, options.resolution, options.source) match {
        case -\/(e) ⇒
          log.error("Subscribe error $e")

        case _ ⇒
          log.info(s"Subscribed to ${contract}")
      }
    }(scala.concurrent.ExecutionContext.Implicits.global)
  } else {
    log.info(s"Already subscribed to ${contract}")
  }
}
*/
