package com.larroy.quant.quotedb

/**
 * @author piotr 22.05.15
 */


import com.larroy.quant.common._
import org.specs2.mutable._
import scala.collection.immutable.IndexedSeq
import scalaz.\/-

class QuoteDBSpec extends Specification {
  "QuoteDBSpec" should {
    "create and remove subscriptions" in new TempDBScope {
      val contract = StockContract("IBM")
      val resolution = Resolution._1_day
      quoteDB.subscribe(contract, resolution)

      var subs = quoteDB.subscriptions()
      subs must have size(1)
      subs(0) should beEqualTo(Subscription(contract, resolution))

      quoteDB.subscribe(contract, resolution)
      subs = quoteDB.subscriptions()
      subs must have size(1)
      subs(0) should beEqualTo(Subscription(contract, resolution))


      quoteDB.subscribe(StockContract("MSFT"), resolution)
      subs = quoteDB.subscriptions()
      subs must have size(2)
      subs(1) should beEqualTo(Subscription(StockContract("MSFT"), resolution))

      quoteDB.unsubscribe(StockContract("MSFT"), resolution)
      subs = quoteDB.subscriptions()
      subs must have size(1)
      subs(0) should beEqualTo(Subscription(contract, resolution))
      //1 should beEqualTo(1)
    }
    "store and update bars" in new TempDBScope {
      val contract = StockContract("IBM")
      val resolution = Resolution._1_day
      var quotes = Array(
        Bar(1432471235040L, 0, 1, 2, 3, 4, 5, ""),
        Bar(1432471235041L, 10, 11, 12, 13, 14, 15, "")
      )
      quoteDB.upsert(contract, quotes, resolution)
      val intervals = quoteDB.availableResolutions(contract)
      intervals.toTraversable must have size(1)
      intervals.head must beEqualTo(resolution)

      quoteDB.quotes(contract) should_===(\/-(quotes.toVector))

      quotes(0) = Bar(1432471235040L, 1, 1, 2, 3, 4, 5, "")
      val quotes2 = Array(
        Bar(1432471235043L, 1, 2, 3, 4, 5, 6, "x"),
        Bar(1432471235046L, 11, 12, 13, 14, 15, 16, "i")
      )
      quoteDB.upsert(contract, quotes, resolution)
      quoteDB.upsert(contract, quotes2, resolution)
      quoteDB.quotes(contract) should_===(\/-(quotes.toVector ++ quotes2.toVector))
    }
  }
}

