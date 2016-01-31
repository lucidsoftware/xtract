package com.lucidchart.open.xtract

import java.util.NoSuchElementException
import scala.collection.GenTraversableOnce
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer

trait ParseError

trait ValidationError extends ParseError

case class TypeError[A](tp: Class[A]) extends ValidationError

trait PathError extends ValidationError {
  def path: XPath
  def extendPath(parentPath: XPath): PathError = {
    withPath(parentPath ++ path)
  }
  protected def withPath(newPath: XPath): PathError
}

case class EmptyError(path: XPath = XPath) extends PathError {
  protected def withPath(newPath: XPath) = EmptyError(newPath)
}

case class MultipleMatchesError(path: XPath = XPath) extends PathError {
  protected def withPath(newPath: XPath) = MultipleMatchesError(newPath)
}

case class MismatchedLabelError(label: String, path: XPath = XPath) extends PathError {
  protected def withPath(newPath: XPath) = MismatchedLabelError(label, newPath)
}

case class RangeError[T](min: T, max: T) extends ValidationError

case class MinCountError(atLeast: Int) extends ValidationError

object ParseResult {
  import play.api.libs.functional._

  def combine[A, B](results: GenTraversableOnce[ParseResult[A]])(implicit canBuild: CanBuildFrom[_, A, B]): ParseResult[B] = {
    val builder = canBuild()
    val errorBuilder = new ListBuffer[ParseError]
    for (res <- results) {
      for (v <- res) builder += v
      errorBuilder ++= res.errors
    }
    val seq = builder.result
    if (errorBuilder.isEmpty) {
      ParseSuccess(seq)
    } else {
      PartialParseSuccess(seq, errorBuilder.result)
    }
  }

  //Used for combinatorial syntax
  implicit val applicativeParseResult: Applicative[ParseResult] = new Applicative[ParseResult] {
    def pure[A](a: A): ParseResult[A] = ParseSuccess(a)
    def map[A, B](m: ParseResult[A], f: A => B): ParseResult[B] = m.map(f)
    def apply[A, B](mf: ParseResult[A => B], ma: ParseResult[A]): ParseResult[B] = (mf, ma) match {
      case (ParseSuccess(f), ParseSuccess(a)) => ParseSuccess(f(a))
      case (ParseSuccess(f), PartialParseSuccess(a, errs)) => PartialParseSuccess(f(a), errs)
      case (PartialParseSuccess(f, errs), ParseSuccess(a)) => PartialParseSuccess(f(a), errs)
      case (PartialParseSuccess(f, errs1), PartialParseSuccess(a, errs2)) => PartialParseSuccess(f(a), errs1 ++ errs2)
      case (mf2, ParseFailure(errs)) => ParseFailure(mf2.errors ++ errs)
      case (ParseFailure(errs), ma2) => ParseFailure(errs ++ ma2.errors)
    }
  }

  implicit def alternativeParseResult(implicit a: Applicative[ParseResult]): Alternative[ParseResult] = new Alternative[ParseResult] {
    val app = a
    def |[A, B >: A](alt1: ParseResult[A], alt2: ParseResult[B]): ParseResult[B] = (alt1, alt2) match {
      case (r, _) if r.isSuccessful => r
      case (_, r) if r.isSuccessful => r
      case (r1, r2) => ParseFailure(r1.errors ++ r2.errors)
    }
    def empty: ParseResult[Nothing] = ParseFailure()
  }



}

sealed trait ParseResult[+A] {
  self =>
  protected def get: A

  def isSuccessful: Boolean
  def errors: Seq[ParseError]

  private def prependErrors(errs: Seq[ParseError]): ParseResult[A] = this match {
    case ParseSuccess(v) => PartialParseSuccess(v, errs)
    case PartialParseSuccess(v, moreErrs) => PartialParseSuccess(v, errs ++ moreErrs)
    case ParseFailure(moreErrs) => ParseFailure(errs ++ moreErrs)
  }

  def map[B](f: A => B): ParseResult[B] = this match {
    case ParseSuccess(v) => ParseSuccess(f(v))
    case PartialParseSuccess(v, errs) => PartialParseSuccess(f(v), errs)
    case failure: ParseFailure => failure
  }

  def flatMap[B](f: A => ParseResult[B]): ParseResult[B] = this match {
    case ParseSuccess(v) => f(v)
    case PartialParseSuccess(v, errs) => f(v).prependErrors(errs)
    case failure: ParseFailure => failure
  }

  /**
   * @param error
   * @param f
   * @return if the result is successful and the filter fails ParseFailure is returned
   */
  def filter(error: => ParseError)(f: A => Boolean): ParseResult[A] =
    filterOrElse(f)(ParseFailure(error))

  def filter(f: A => Boolean): ParseResult[A] =
    filterOrElse(f)(ParseFailure(Nil))

  def withFilter(f: A => Boolean): ParseResult[A] = filter(f)

  private def filterOrElse[B >: A](f: A => Boolean)(failed: => ParseResult[B]): ParseResult[B] =
    if (!isSuccessful || f(get)) {
      this
    } else {
      failed
    }

  @inline
  def foreach(f: A => Unit): Unit = if (isSuccessful) {
    f(get)
  }

  final def collect[B](otherwise: => ParseError)(pf: PartialFunction[A,B]): ParseResult[B] = flatMap{ v =>
    if (pf.isDefinedAt(v)) {
      ParseSuccess(pf(v))
    } else {
      ParseFailure(otherwise)
    }
  }

  final def collect[B](pf: PartialFunction[A,B]): ParseResult[B] = flatMap { v =>
    if (pf.isDefinedAt(v)) {
      ParseSuccess(pf(v))
    } else {
      ParseFailure()
    }
  }

  def recoverPartial[B >: A](otherwise: => B): ParseResult[B] = if (isSuccessful) {
    this
  } else {
    PartialParseSuccess(otherwise, errors)
  }

  def orElse[B >: A](otherwise: => ParseResult[B]): ParseResult[B] = if (isSuccessful) this else otherwise
  def getOrElse[B >: A](otherwise: => B): B = if (isSuccessful) get else otherwise
  def toOption: Option[A] = if (isSuccessful) Some(get) else None
}


case class ParseSuccess[+A](get: A) extends ParseResult[A] {
  def isSuccessful = true
  def errors: Seq[ParseError] = Nil

}

case class PartialParseSuccess[+A](get: A, errors: Seq[ParseError]) extends ParseResult[A] {
  def isSuccessful = true
}

object ParseFailure {
  def apply(): ParseFailure = ParseFailure(Nil)
  def apply(error: ParseError): ParseFailure = ParseFailure(List(error))
}
case class ParseFailure(errors: Seq[ParseError]) extends ParseResult[Nothing] {
  def isSuccessful = false
  protected def get: Nothing = throw new NoSuchElementException //this should be unreachable
}
