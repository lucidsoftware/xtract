package com.lucidchart.open.xtract

import scala.language.experimental.macros
import scala.util.control.NonFatal
import scala.xml.NodeSeq

object XmlReader extends DefaultXmlReaders with XmlReaderExtensions {
  /**
   * Create a new [[XmlReader]] from a function that converts a NodeSeq to a [[ParseResult]].
   * @param f A transformation function for the transformation done by the [[XmlReader]]
   */
  def apply[A](f: NodeSeq => ParseResult[A]): XmlReader[A] = new XmlReader[A] {
    def read(xml: NodeSeq): ParseResult[A] = f(xml)
  }

  import play.api.libs.functional._
  implicit def applicative(implicit applicativeResult: Applicative[ParseResult]): Applicative[XmlReader] = new Applicative[XmlReader] {
    def pure[A](a: A): XmlReader[A] = XmlReader { _ => ParseSuccess(a)}

    def map[A, B](m: XmlReader[A], f: A => B): XmlReader[B] = m.map(f)

    def apply[A, B](mf: XmlReader[A => B], ma: XmlReader[A]): XmlReader[B] = XmlReader { xml =>
      applicativeResult(mf.read(xml), ma.read(xml))
    }
  }

  implicit def alternative(implicit a: Applicative[XmlReader]): Alternative[XmlReader] = new Alternative[XmlReader] {
    val app = a
    override def `|`[A, B >: A](alt1: XmlReader[A], alt2: XmlReader[B]): XmlReader[B] = XmlReader { xml =>
      val r = alt1.read(xml)
      if (r.isSuccessful) {
        r
      } else {
        val r2 = alt2.read(xml)
        if (r2.isSuccessful) {
          r2
        } else {
          ParseFailure(r.errors ++ r2.errors)
        }
      }
    }

    override def empty: XmlReader[Nothing] = XmlReader{ _ => ParseFailure()}
  }

  implicit def functor(implicit a: Applicative[XmlReader]) = new Functor[XmlReader] {
    def fmap[A, B](reader: XmlReader[A], f: A => B): XmlReader[B] = a.map(reader, f)
  }

  /**
   * Get an implicit [[XmlReader]] for a type
   * @tparam A The result type of the desired [[XmlReader]]
   * @param r The implicit [[XmlReader]] to use.
   */
  def of[A](implicit r: XmlReader[A]): XmlReader[A] = r

  /**
   * Define an [[XmlReader]] for type `A` from a list of [[ReadLine]]s.
   * @tparam A The type to create the [[XmlReader]] for.
   * @param lines A list of [[ReadLine]]s describe the fields to parse. These should correspond to
   * the parameters for one of the constructors of `A`. There should be an implicit [[XmlReader]] in
   * scope for the type of each [[ReadLine]].
   */
  def makeReader[A](lines: ReadLine[_]*): XmlReader[A] = macro ReaderBuilder.buildReader[A]

  /**
   * Define an [[XmlReader]] for type `A`, inferring the types from primary
   * constructor of `A`.
   * @tparam A The type to create the [[XmlReader]] for.
   *
   * There should be an implicit [[XmlReader]] in scope for the type of
   * each parameter to `A`s primary constructor.
   *
   * For a class defined like:
   * {{{
   * case class A(a: Int, b: String, c: Option[Double])
   * object A {
   *   implicit val reader = XmlReader.makeReader[A]
   * }
   * }}}
   *
   * the reader defined is equivalent to:
   *
   * {{{
   * for {
   *   a <- (__ \ "a").read[Int]
   *   b <- (__ \ "b").read[String]
   *   c <- (__ \ "c").read[Option[Double]]
   * } yield new A(a, b, c)
   * }}}
   */
  def makeReader[A]: XmlReader[A] = macro ReaderBuilder.inferReader[A]
}

/**
 * An abstraction for a function that takes a NodeSeq and returns
 * a [[ParseResult]].
 *
 * It is used to parse XML to arbitrary scala objects, and supports combinatorial syntax
 * to easily compose [[XmlReader]]s into new [[XmlReader]]s.
 */
trait XmlReader[+A] {

  /**
   * The core operation of an [[XmlReader]], it converts an xml NodeSeq
   * into a ParseResult of the desired type.
   * @param xml The xml to read from.
   * @return The [[ParseResult]] resulting from reading the xml.
   */
  def read(xml: NodeSeq): ParseResult[A]

  /**
   * Map the [[XmlReader]].
   * This converts one [[XmlReader]] into another (usually of a different type).
   * @param f The mapping function.
   * @return A new XmlReader that succeeds with result of calling `f` on the result of
   *   this if this succeeds.
   */
  def map[B](f: A => B): XmlReader[B] = XmlReader{ xml => this.read(xml).map(f)}

  /**
   * Try to map, and if there is an exception,
   * return a failure with the supplied error
   * @param fe A function that returns the appropriate [[ParseError]] if mapping failed.
   * @param f The mapping function.
   * @tparam B The type to map into.
   * @return
   */
  def tryMap[B](fe: A => ParseError)(f: A => B): XmlReader[B] = XmlReader { xml =>
     this.read(xml).flatMap { x =>
      try {
        ParseSuccess(f(x))
      } catch {
        case NonFatal(_) => ParseFailure(fe(x))
      }
    }
  }

  /**
   * Similar to [[map]] but does a flatMap on the [[ParseResult]] rather than
   * a map.
   */
  def flatMap[B](f: A => XmlReader[B]): XmlReader[B] = XmlReader { xml =>
    this.read(xml).flatMap(t => f(t).read(xml))
  }

  /**
   * Filter the result of the [[XmlReader]].
   * It filters the resulting [[ParseResult]] after reading.
   * @param p The predicate to filter with
   */
  def filter(p: A => Boolean): XmlReader[A] = XmlReader { xml => read(xml).filter(p)}

  /**
   * Similar to [[filter(p:A=>Boolean):*]], but allows you to supply the [[ParseError]]
   * to use if the filter test fails.
   * @param error The error to use if the filter fails.
   * @param p The predicate to filter with
   */
  def filter(error: => ParseError)(p: A => Boolean): XmlReader[A] =
    XmlReader { xml => read(xml).filter(error)(p) }

  /**
   * Map a partial function over the [[XmlReader]]. If the partial function isn't
   * defined for the input, returns a [[ParseFailure]] containing `otherwise` as its error.
   */
  def collect[B](otherwise: => ParseError)(f: PartialFunction[A, B]): XmlReader[B] =
    XmlReader { xml => read(xml).collect(otherwise)(f) }

  /**
   * Map a partial function over the [[XmlReader]]. If the partial function isn't
   * defined for the input, returns a [[ParseFailure]].
   */
  def collect[B](f: PartialFunction[A, B]): XmlReader[B] =
    XmlReader { xml => read(xml).collect(f) }

  /**
   * @return New [[XmlReader]] that succeeds if either this or `v` succeeds
   *   on the input. this has preference.
   */
  def orElse[B >: A](v: => XmlReader[B]): XmlReader[B] = XmlReader { xml =>
    read(xml).orElse(v.read(xml))
  }

  /**
   * Compose this [[XmlReader]] with another.
   * @param r An [[XmlReader]] that returns a NodeSeq result.
   * @return New [[XmlReader]] that uses this [[XmlReader]] to read the result of r.
   */
  def compose[B <: NodeSeq](r: XmlReader[B]): XmlReader[A] = XmlReader { xml =>
    r.read(xml).flatMap(read(_))
  }

  /**
   * Similar to [[compose]] but with the operands reversed.
   * @param other The [[XmlReader]] to compose this with.
   */
  def andThen[B](other: XmlReader[B])(implicit  witness: <:<[A, NodeSeq]): XmlReader[B] = other.compose(this.map(witness))

  /**
   * Convert to a reader that always succeeds with an option (None if it would have failed). Any errors are dropped
   * @return
   */
  def optional: XmlReader[Option[A]] = XmlReader { xml =>
    ParseSuccess(read(xml).toOption)
  }

  /**
   * Use a default value if unable to parse, always successful, drops any errors
   * @param v
   * @return
   */
  def default[B >: A](v: B): XmlReader[B] = XmlReader { xml =>
    ParseSuccess(read(xml).getOrElse(v))
  }



  /**
   * Recover from a failed parse, keeping any errors.
   * @param otherwise
   * @tparam B
   * @return
   */
  def recover[B >: A](otherwise: B): XmlReader[B] = XmlReader { xml =>
    read(xml).recoverPartial(otherwise)
  }
}
