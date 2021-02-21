package com.lucidchart.open.xtract

import scala.language.experimental.macros

package object meta {
  /**
   * Define an [[com.lucidchart.open.xtract.XmlReader]] for type `A` from a list of [[ReadParam]]s.
   * @tparam A The type to create the [[com.lucidchart.open.xtract.XmlReader]] for.
   * @param lines A list of [[ReadParam]]s describe the fields to parse. These should correspond to
   * the parameters for one of the constructors of `A`. There should be an implicit [[XmlReader]] in
   * scope for the type of each [[ReadParam]].
   */
  def makeReader[A](lines: ReadParam[_]*): XmlReader[A] = macro ReaderBuilder.buildReader[A]

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
