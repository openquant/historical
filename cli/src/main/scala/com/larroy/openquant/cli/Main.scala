package com.larroy.openquant.cli

import java.time.{ZoneId, LocalDate}
import java.time.format.DateTimeFormatter

import com.larroy.quant.common._

import scala.util.control.NonFatal
import scala.util.{Failure, Try}
import scalaz.{-\/, \/-, Success, \/}

/**
  * CLI to handle subscriptions and historical data
  */
object Main extends Logging {
  private val version = "0.1"

  val startEndDateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")
  val startEndDateValidDateRe = """(\d{8}) (\d{2}:\d{2}:\d{2}) ?(\w*)?""".r

  val expiryDateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
  val expiryValidDateRe = """(\d{8})""".r

  def getOptionParser: scopt.OptionParser[Options] = {
    new scopt.OptionParser[Options]("cli") {
      head("quoter", Main.version)

      override def showUsageOnError: Boolean = true

      val contractOptionDef = List(
        opt[String]('s', "contract") text ("contract") action {
          (arg, o) => o.copy(symbol = arg)
        },
        opt[String]('t', "type") text ("type") action {
          (arg, o) => o.copy(contractType = ContractType.withName(arg))
        },
        opt[String]('i', "resolution") text ("resolution") action {
          (arg, o) => o.copy(resolution = Resolution.withName(arg))
        },
        note(s"resolution is one of: '${Resolution.values.mkString(", ")}'"),
        note(s"type is one of: '${ContractType.values.mkString(", ")}'"),
        opt[String]('e', "exchange") text ("exchange") action {
          (arg, o) => o.copy(exchange = arg)
        },
        opt[String]('c', "currency") text ("currency") action {
          (arg, o) => o.copy(currency = arg)
        },
        opt[String]('y', "expiry") text ("expiry") action { (arg, o) =>
          val maybeExpiry = Some(LocalDate.parse(arg, expiryDateTimeFormat).atStartOfDay().atZone(ZoneId.systemDefault))
          o.copy(maybeExpiry = maybeExpiry)
        } validate {
          case expiryValidDateRe(_*) ⇒ success
          case _ ⇒ failure(s"argument doesn't match ${expiryValidDateRe.toString}")
        }
      )

      val sourceAndSinkOptionDef = contractOptionDef ++ List(
        opt[String]('r', "source") text ("source") action {
          (arg, o) => o.copy(source = arg)
        } validate {
          case s if s matches "(?i)yahoo" ⇒ success
          case s if s matches "(?i)ib" ⇒ success
          case _ ⇒ failure(s"source must be one of [yahoo,ib]")
        },
        note(s"source is one of: [yahoo,ib]"),
        opt[String]('d', "dburl") text ("dburl") action {
          (arg, o) => o.copy(quoteDBUrl = arg)
        }
      )

      help("help") text ("print help")

      /*
       * Common arguments
       */
      opt[Boolean]('q', "quiet") text ("suppress progress on stdout") action {
        (arg, o) => o.copy(quiet = arg)
      }

      /*
       * Git style subcommands
       */
      cmd("update") text ("update") text ("Example: update -s MSFT") action {
        (_, o) => o.copy(mode = Mode.Update)
      } children (sourceAndSinkOptionDef: _*)

      cmd("subscribeall") action {
        (_, o) => o.copy(mode = Mode.SubscribeAll)
      }

      cmd("subscribe") action {
        (_, o) => o.copy(mode = Mode.Subscribe)
      } children (sourceAndSinkOptionDef: _*)
    }
  }

  def main(args: Array[String]) {
    val optionParser = getOptionParser
    val options: Options = optionParser.parse(args, Options()).getOrElse {
      log.error("Option syntax incorrect")
      log.error(s"Arguments given ${args.mkString("'", "' '", "'")}")
      log.error("Failure.")
      sys.exit(1)
    }

    def exitSuccess(): Unit = {
      log.info("Success.")
      log.info("=========== finished successfully ================")
      sys.exit(0)
    }

    def exitFailure(): Unit = {
      log.error("Failure.")
      log.info("=========== finished with errors =================")
      sys.exit(-1)
    }

    def exitDisjunction(x: Throwable \/ Any): Unit = x match {
      case \/-(_) ⇒ exitSuccess()
      case -\/(e) ⇒
        log.error(s"Exception: $e")
        exitFailure()
    }

    val res = options.mode match {
      case Mode.Invalid ⇒
        optionParser.reportError("Please specify a valid command")
        optionParser.showUsage
        exitFailure()

      case Mode.Update ⇒
       exitDisjunction(Update(options))

      case Mode.SubscribeAll ⇒
        exitDisjunction(Subscribe.all(options))

      case Mode.Subscribe ⇒
        exitDisjunction(Subscribe(options))

      case x =>
        log.error(s"Unknown mode: $x")
        exitFailure()
    }
  }
}
