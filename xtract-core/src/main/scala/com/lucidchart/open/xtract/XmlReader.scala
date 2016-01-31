package com.lucidchart.open.xtract

import scala.util.control.NonFatal
import scala.xml.NodeSeq

object XmlReader extends DefaultXmlReaders with XmlReaderExtensions {
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

  def of[A](implicit r: XmlReader[A]): XmlReader[A] = r

}

trait XmlReader[+A] {

  def read(xml: NodeSeq): ParseResult[A]

  def map[B](f: A => B): XmlReader[B] = XmlReader{ xml => this.read(xml).map(f)}

  /**
   * Try to map, and if there is an exception,
   * return a failure with the supplied error
   * @param fe
   * @param f
   * @tparam B
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

  def flatMap[B](f: A => XmlReader[B]): XmlReader[B] = XmlReader { xml =>
    this.read(xml).flatMap(t => f(t).read(xml))
  }

  def filter(p: A => Boolean): XmlReader[A] = XmlReader { xml => read(xml).filter(p)}

  def filter(error: => ParseError)(p: A => Boolean): XmlReader[A] =
    XmlReader { xml => read(xml).filter(error)(p) }

  def collect[B](otherwise: => ParseError)(f: PartialFunction[A, B]): XmlReader[B] =
    XmlReader { xml => read(xml).collect(otherwise)(f) }

  def collect[B](f: PartialFunction[A, B]): XmlReader[B] =
    XmlReader { xml => read(xml).collect(f) }

  def orElse[B >: A](v: => XmlReader[B]): XmlReader[B] = XmlReader { xml =>
    read(xml).orElse(v.read(xml))
  }

  def compose[B <: NodeSeq](r: XmlReader[B]): XmlReader[A] = XmlReader { xml =>
    r.read(xml).flatMap(read(_))
  }

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
