package com.larroy.openquant.cli

import java.time.ZonedDateTime

import com.larroy.quant.common.{Resolution, ContractType}

case class Options(
  mode: Mode.Enum = Mode.Invalid,
  quiet: Boolean = false,
  symbol: String = "",
  contractType: ContractType.Enum = ContractType.Stock,
  exchange: String = "NYSE",
  currency: String = "USD",
  maybeExpiry: Option[ZonedDateTime] = None,
  maybeStartDate: Option[ZonedDateTime] = None,
  maybeEndDate: Option[ZonedDateTime] = None,
  resolution: Resolution.Enum = Resolution._1_day,
  quoteDBUrl: String = "",
  source: String = "yahoo"
)
