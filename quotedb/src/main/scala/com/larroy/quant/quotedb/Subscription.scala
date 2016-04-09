package com.larroy.quant.quotedb

import java.time.ZonedDateTime
import com.larroy.quant.common.{Resolution, Contract}

/**
 * @author piotr 22.05.15
 */
case class Subscription(
  contract: Contract,
  resolution: Resolution.Enum,
  lastUpdateSuccess: Option[ZonedDateTime] = None,
  lastUpdateAttempt: Option[ZonedDateTime] = None,
  lastUpdateStatus: Option[UpdateStatus.Enum] = None,
  source: String = "")
