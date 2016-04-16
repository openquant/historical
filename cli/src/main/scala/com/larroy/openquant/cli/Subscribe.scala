package com.larroy.openquant.cli

import akka.actor.ActorSystem
import com.larroy.openquant.quoteprovider.QuoteProviderFactoryLoader
import com.larroy.openquant.symbols.{Exchange, StockInfo, Symbols}
import com.larroy.quant.common._
import com.larroy.quant.common.utils.ThrottlingExecutionContext
import com.larroy.quant.quotedb._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scalaz.{-\/, \/, \/-}

//import scalaz._
//import Scalaz._
import akka.stream._
import akka.stream.scaladsl._


trait SubscriptionStatus {
  val contract: Contract
}

case class SubscriptionSuccess(override val contract: Contract) extends SubscriptionStatus

case class AlreadySubscribed(override val contract: Contract) extends SubscriptionStatus

case class SubscriptionFailure(override val contract: Contract, msg: String) extends SubscriptionStatus


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
    implicit val ec = system.dispatcher

    val decider: Supervision.Decider = {
      case _ ⇒ Supervision.Resume
    }
    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))


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
    def subscribe(stockInfo: StockInfo): Future[SubscriptionStatus] = {
      val contract = Contract(stockInfo.ticker, options.contractType, options.exchange, options.currency, options.maybeExpiry)
      if (quoteDB.subscription(contract, options.resolution).isLeft) {
        quoteProvider.available(contract, options.resolution).map { x ⇒
          quoteDB.subscribe(contract, options.resolution, options.source) match {
            case \/-(_) ⇒
              SubscriptionSuccess(contract)
            case -\/(e) ⇒
              SubscriptionFailure(contract, e.getMessage)
          }
        }
      } else {
        Future.successful(AlreadySubscribed(contract))
      }
    }
    val res = stockSource.mapAsyncUnordered(10)(subscribe)
    val r = res.runForeach(x ⇒ log.debug(s"$x"))
    Await.result(r, Duration.Inf)
    log.debug("done")
    materializer.shutdown()
    system.terminate()
    \/-(Unit)
  }
}
