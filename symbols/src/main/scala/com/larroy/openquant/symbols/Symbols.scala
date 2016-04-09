package com.larroy.openquant.symbols

import java.io.InputStreamReader

import scala.util.Try

/**
  * Provides list of symbols for different markets
  */
object Symbols {

  val capre = """\$(\d+(?:.\d+))([MB])""".r

  def listExchange(exchange: Exchange.Enum): Vector[StockInfo] = {
    val resPath: String = exchange match {
      case Exchange.NYSE => "/nyse.csv"
      case Exchange.AMEX => "/amex.csv"
      case Exchange.NASDAQ => "/nasdaq.csv"
    }
    val is = getClass.getResourceAsStream(resPath)
    if (is == null)
      List.empty[StockInfo]
    import com.github.tototoshi.csv._
    val rdr = CSVReader.open(new InputStreamReader(is))
    rdr.toStream.drop(1).map(parseCSVLine).toVector
  }

  def parseCSVLine(x: List[String]): StockInfo = {
    val IPOyear = Try(x(4).toInt).toOption
    new StockInfo(x(0), x(1), getCapMill(x(3)), IPOyear, x(5), x(6), x(7))
  }

  /// Capitalization from $13.B or $512.5M or n/a in Millions
  def getCapMill(x: String): Option[BigDecimal] = {
    x match {
      case capre(dollars, unit) => {
        unit match {
          case "B" =>
            Some(BigDecimal(dollars) * 1000)
          case "M" =>
            Some(BigDecimal(dollars))
          case _ =>
            None
        }
      }
      case _ => None
    }
  }
}
