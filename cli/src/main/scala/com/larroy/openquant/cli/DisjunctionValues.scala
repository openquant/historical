package com.larroy.openquant.cli

import scalaz.{-\/, \/, \/-}

object DisjunctionValues {

  import scala.language.implicitConversions

  implicit def convertDisjunctionToDisjunctionated[E, T](disjunction: E \/ T) = new Disjunctionable(disjunction)


  class Disjunctionable[E, T](disjunction: E \/ T) {
    def value: T = {
      disjunction match {
        case \/-(right) =>
          right
        case -\/(left) =>
          throw new RuntimeException(s"$left is -\\/, expected \\/-.")
      }
    }

    /**
      * Allow .leftValue on an \/ to extract the left side. Like .value, but for the left.
      */
    def leftValue: E = {
      disjunction match {
        case \/-(right) =>
          throw new RuntimeException(s"$right is \\/-, expected -\\/.")
        case -\/(left) =>
          left
      }
    }
  }
}
