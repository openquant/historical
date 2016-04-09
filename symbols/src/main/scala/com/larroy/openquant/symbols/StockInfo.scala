package com.larroy.openquant.symbols

case class StockInfo(
  ticker: String,
  name: String,
  capM: Option[BigDecimal],
  IPOyear: Option[Int],
  Sector: String,
  Industry: String,
  url: String)
