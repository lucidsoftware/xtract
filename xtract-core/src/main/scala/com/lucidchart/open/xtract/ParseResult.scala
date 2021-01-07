package com.lucidchart.open.xtract

import java.util.NoSuchElementException
import scala.collection.compat._
import scala.collection.mutable.ListBuffer

/**
 * Top level types for errors encountered while reading xml with
 * an [[XmlReader]].
 */
trait ParseError

/**
 * A [[ParseError]] caused by some sort of failed validation.
 */
trait ValidationError extends ParseError

/**
 * A [[ValidationError]] indicating that the input wasn't the correct type.
 * For example, if an integer was expected, but the input text couldn't be
 * parsed as an integer.
 *
 * @param tp The Class opject for the expected type.
 */
case class TypeError[A](tp: Class[A]) extends ValidationError

/**
 * A [[ValidationError]] indicating that the input NodeSeq
 * didn't have the expected XPath.
 */
trait PathError extends ValidationError {
  /**
   * The expected path.
   */
  def path: XPath
  /**
   * Return the same [[PathError]] but with another path preprended.
   */
  def extendPath(parentPath: XPath): PathError = {
    withPath(parentPath ++ path)
  }
  /**
   * @return a copy of this, but with a different path.
   */
  protected def withPath(newPath: XPath): PathError
}

/**
 *  A [[PathError]] indicating that the path was missing.
 */
case class EmptyError(path: XPath = XPath) extends PathError {
  protected def withPath(newPath: XPath) = EmptyError(newPath)
}

/**
 * A [[PathError]] indicating that the path matched multiple nodes, and only
 * one was expected.
 */
case class MultipleMatchesError(path: XPath = XPath) extends PathError {
  protected def withPath(newPath: XPath) = MultipleMatchesError(newPath)
}

/**
 * A [[PathError]] indicating that the node(s) at the specified path did not
 * have the expected tag label.
 */
case class MismatchedLabelError(label: String, path: XPath = XPath) extends PathError {
  protected def withPath(newPath: XPath) = MismatchedLabelError(label, newPath)
}

/**
 *  A [[ValidationError]] indicating the value was outside the expected range.
 */
case class RangeError[T](min: T, max: T) extends ValidationError

/**
 *  A [[ValidationError]] indicating there weren't enough entries.
 */
case class MinCountError(atLeast: Int) extends ValidationError

object ParseResult {
  import cats.Applicative

  /**
   * Combine a collection of [[ParseResult]]s into a [[ParseResult]] of a collection.
   *
   * If any of the input [[ParseResult]]s has errors, then a [[PartialParseSuccess]] is
   * returned with both the container of [[ParseSuccess]]es and a list of all errors.
   */
  def combine[A, B](results: IterableOnce[ParseResult[A]])(implicit factory: CollectionFactory[A, B]): ParseResult[B] = {
    val builder = factory.newBuilder
    val errorBuilder = new ListBuffer[ParseError]
    for (res <- results.iterator) {
      for (v <- res) builder += v
      errorBuilder ++= res.errors
    }
    val seq = builder.result()
    if (errorBuilder.isEmpty) {
      ParseSuccess(seq)
    } else {
      PartialParseSuccess(seq, errorBuilder.result())
    }
  }

  //Used for combinatorial syntax
  implicit object algebra extends Applicative[ParseResult] {
    def pure[A](a: A): ParseResult[A] = ParseSuccess(a)
    def ap[A, B](ff: ParseResult[A => B])(fa: ParseResult[A]): ParseResult[B] = (ff, fa) match {
      case (ParseSuccess(f), ParseSuccess(a)) => ParseSuccess(f(a))
      case (ParseSuccess(f), PartialParseSuccess(a, errs)) => PartialParseSuccess(f(a), errs)
      case (PartialParseSuccess(f, errs), ParseSuccess(a)) => PartialParseSuccess(f(a), errs)
      case (PartialParseSuccess(f, errs1), PartialParseSuccess(a, errs2)) => PartialParseSuccess(f(a), errs1 ++ errs2)
      case (mf2, ParseFailure(errs)) => ParseFailure(mf2.errors ++ errs)
      case (ParseFailure(errs), ma2) => ParseFailure(errs ++ ma2.errors)
    }

    override def map[A, B](fa: ParseResult[A])(f: A => B): ParseResult[B] = fa.map(f)

    override def product[A, B](fa: ParseResult[A], fb: ParseResult[B]): ParseResult[(A,B)] = (fa, fb) match {
      case (ParseSuccess(a), ParseSuccess(b)) => ParseSuccess((a, b))
      case (ParseSuccess(a), PartialParseSuccess(b, errs)) => PartialParseSuccess((a, b), errs)
      case (PartialParseSuccess(a, errs), ParseSuccess(b)) => PartialParseSuccess((a, b), errs)
      case (PartialParseSuccess(a, errs1), PartialParseSuccess(b, errs2)) => PartialParseSuccess((a, b), errs1 ++ errs2)
      case (a, ParseFailure(errs)) => ParseFailure(a.errors ++ errs)
      case (ParseFailure(errs), b) => ParseFailure(errs ++ b.errors)
    }
  }
}

/**
 * Trait containing the result of an [[XmlReader]].
 * It can contain one of the following:
 *
 *  - [[ParseSuccess]] indicates a completely successful result
 *  - [[ParseFailure]] indicates a completely failed result
 *  - [[PartialParseSuccess]] indicates there were recoverable errors
 *    and contains both the result and a list of errors that occurred.
 */
sealed trait ParseResult[+A] {
  self =>
  protected def get: A

  /**
   * Is the result a success, i.e. either a [[ParseSuccess]] or a [[PartialParseSuccess]].
   */
  def isSuccessful: Boolean
  /**
   * A sequence of all [[ParseError]]s that occurred.
   */
  def errors: Seq[ParseError]

  private def prependErrors(errs: Seq[ParseError]): ParseResult[A] = this match {
    case ParseSuccess(v) => PartialParseSuccess(v, errs)
    case PartialParseSuccess(v, moreErrs) => PartialParseSuccess(v, errs ++ moreErrs)
    case ParseFailure(moreErrs) => ParseFailure(errs ++ moreErrs)
  }

  /**
   * Map a function over the result value.
   * The level of success, and any errors are propagated.
   */
  def map[B](f: A => B): ParseResult[B] = this match {
    case ParseSuccess(v) => ParseSuccess(f(v))
    case PartialParseSuccess(v, errs) => PartialParseSuccess(f(v), errs)
    case failure: ParseFailure => failure
  }

  /**
   * FlatMap over the result value.
   *
   * One thing to note is that if this is a [[PartialParseSuccess]], then
   * the errors are prepended to the result of calling `f` on the value.
   * So for example, if `f` returns a [[ParseSuccess]], but this is a [[PartialParseSuccess]]
   * the final result will be a [[PartialParseSuccess]] containing the result from `f` and the
   * errors from this.
   */
  def flatMap[B](f: A => ParseResult[B]): ParseResult[B] = this match {
    case ParseSuccess(v) => f(v)
    case PartialParseSuccess(v, errs) => f(v).prependErrors(errs)
    case failure: ParseFailure => failure
  }

  /**
   * @param error The error to use if the test fails.
   * @param f Predicate to filter the [[ParseResult]] by
   * @return if the result is successful and the filter fails ParseFailure is returned
   */
  def filter(error: => ParseError)(f: A => Boolean): ParseResult[A] =
    filterOrElse(f)(ParseFailure(error))

  /**
   * @param f Predicate to filter the [[ParseResult]] by
   * @return this if the test passes, or [[ParseFailure]] if the test fails.
   */
  def filter(f: A => Boolean): ParseResult[A] =
    filterOrElse(f)(ParseFailure(Nil))

  /**
   * Alias for filter
   */
  def withFilter(f: A => Boolean): ParseResult[A] = filter(f)

  private def filterOrElse[B >: A](f: A => Boolean)(failed: => ParseResult[B]): ParseResult[B] =
    if (!isSuccessful || f(get)) {
      this
    } else {
      failed
    }

  /**
   * Execute `f` on the result value if and only if this [[ParseResult]] is successful.
   * @param f the function to execute.
   */
  @inline
  def foreach(f: A => Unit): Unit = if (isSuccessful) {
    f(get)
  }

  /**
   * Handle both success and failure.
   * @param onFailure Function to handle a [[ParseFailure]].
   * @param onSuccess Function to process a successful parse.
   */
  def fold[B](onFailure: Seq[ParseError] => B)(onSuccess: A => B): B = {
    if (isSuccessful) {
      onSuccess(get)
    } else {
      onFailure(errors)
    }
  }

  /**
   * Apply a PartialFunction to the result value.
   * If the `pf` isn't defined for the result value, a [[ParseFailure]] with the `otherwise` error
   * is returned.
   */
  final def collect[B](otherwise: => ParseError)(pf: PartialFunction[A,B]): ParseResult[B] = flatMap{ v =>
    if (pf.isDefinedAt(v)) {
      ParseSuccess(pf(v))
    } else {
      ParseFailure(otherwise)
    }
  }

  /**
   * Apply a PartialFunction to the result value.
   * If the `pf` function isn't defined for the result value a [[ParseFailure]] is returned.
   */
  final def collect[B](pf: PartialFunction[A,B]): ParseResult[B] = flatMap { v =>
    if (pf.isDefinedAt(v)) {
      ParseSuccess(pf(v))
    } else {
      ParseFailure()
    }
  }

  /**
   * If this is a [[ParseFailure]] recover as a [[PartialParseSuccess]]
   * containing `otherwise` so that
   * the errors are kept.
   */
  def recoverPartial[B >: A](otherwise: => B): ParseResult[B] = if (isSuccessful) {
    this
  } else {
    PartialParseSuccess(otherwise, errors)
  }

  /**
   * @return this if this is successful, otherwise return other if other is
   * successful, and a `ParseFailure` containing errors from both if both failed.
   */
  def or[B >: A](other: ParseResult[B]): ParseResult[B] = {
    if (isSuccessful) {
      this
    } else if (other.isSuccessful) {
      other
    } else {
      ParseFailure(this.errors ++ other.errors)
    }
  }

  /**
   * Alias for `or`
   */
  def |[B >: A](other: ParseResult[B]) = or(other)

  /**
   * Like `or`, but take `otherwise` as a by-name parameter, and
   * don't combine errors from both.
   *
   * @return this if this is successful otherwise return `otherwise`.
   */
  def orElse[B >: A](otherwise: => ParseResult[B]): ParseResult[B] = if (isSuccessful) this else otherwise
  /**
   * @return the result value, unless this is a [[ParseFailure]] in which case return `otherwise`.
   */
  def getOrElse[B >: A](otherwise: => B): B = if (isSuccessful) get else otherwise
  /**
   * Convert to an Option
   * @return a Some containing the result if successful, or a None if a failure
   */
  def toOption: Option[A] = if (isSuccessful) Some(get) else None
}


/**
 * A [[ParseResult]] indicating a completely successful parse.
 */
case class ParseSuccess[+A](get: A) extends ParseResult[A] {
  def isSuccessful = true
  def errors: Seq[ParseError] = Nil

}

/**
 * A [[ParseResult]] indicating a successful parse that had recoverable errors.
 *
 * Contains both a result value and a list of errors.
 */
case class PartialParseSuccess[+A](get: A, errors: Seq[ParseError]) extends ParseResult[A] {
  def isSuccessful = true
}

object ParseFailure {
  /**
   * Create a [[ParseFailure]] with no associated errors.
   */
  def apply(): ParseFailure = ParseFailure(Nil)
  /**
   * Create a [[ParseFailure]] with a singel error.
   */
  def apply(error: ParseError): ParseFailure = ParseFailure(List(error))
}
/**
 * A [[ParseResult]] indicating a failed
 */
case class ParseFailure(errors: Seq[ParseError]) extends ParseResult[Nothing] {
  def isSuccessful = false
  protected def get: Nothing = throw new NoSuchElementException //this should be unreachable
}
