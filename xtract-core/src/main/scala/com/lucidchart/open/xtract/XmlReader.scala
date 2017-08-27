package com.lucidchart.open.xtract

import scala.util.control.NonFatal
import scala.xml.NodeSeq

object XmlReader extends DefaultXmlReaders with XmlReaderExtensions {
  import cats.Applicative

  /**
   * Create a new [[XmlReader]] from a function that converts a NodeSeq to a [[ParseResult]].
   * @param f A transformation function for the transformation done by the [[XmlReader]]
   */
  def apply[A](f: NodeSeq => ParseResult[A]): XmlReader[A] = new XmlReader[A] {
    def read(xml: NodeSeq): ParseResult[A] = f(xml)
  }

  implicit object algebra extends Applicative[XmlReader] {
    def pure[A](a: A): XmlReader[A] = new XmlReader[A] {
      def read(xml: NodeSeq): ParseResult[A] = ParseSuccess(a)
    }

    def ap[A, B](ff: XmlReader[A => B])(fa: XmlReader[A]): XmlReader[B] = new XmlReader[B] {
      def read(xml: NodeSeq): ParseResult[B] = ParseResult.algebra.ap(ff.read(xml))(fa.read(xml))
    }

    override def map[A, B](fa: XmlReader[A])(f: A => B): XmlReader[B] = fa.map(f)

    override def product[A, B](fa: XmlReader[A], fb: XmlReader[B]): XmlReader[(A,B)] = new XmlReader[(A,B)] {
      def read(xml: NodeSeq): ParseResult[(A,B)] = ParseResult.algebra.product(fa.read(xml), fb.read(xml))
    }
  }

  /**
   * Get an implicit [[XmlReader]] for a type
   * @tparam A The result type of the desired [[XmlReader]]
   * @param r The implicit [[XmlReader]] to use.
   */
  def of[A](implicit r: XmlReader[A]): XmlReader[A] = r

}

/**
 * An abstraction for a function that takes a NodeSeq and returns
 * a [[ParseResult]].
 *
 * It is used to parse XML to arbitrary scala objects, and supports combinatorial syntax
 * to easily compose [[XmlReader]]s into new [[XmlReader]]s.
 */
trait XmlReader[+A] { outer =>

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
   *   on the input. This has preference. If both fail the [[ParseFailure]]
   *   contains the errors from both.
   */
  def or[B >: A](other: XmlReader[B]): XmlReader[B] = new XmlReader[B] {
    def read(xml: NodeSeq): ParseResult[B] = {
      val r = outer.read(xml)
      if (r.isSuccessful) {
        r
      } else {
        val r2 = other.read(xml)
        if (r2.isSuccessful) {
          r2
        } else {
          ParseFailure(r.errors ++ r2.errors)
        }
      }
    }
  }

  /**
   * Alias for `or`
   */
  def |[B >: A](other: XmlReader[B]) = or(other)

  /**
   * Like `or` but takes a by-name parameter and doesn't combine errors.
   *
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
