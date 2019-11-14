package com.lucidchart.open.xtract

import scala.language.implicitConversions
import scala.xml.{Node, NodeSeq}

trait DefaultXmlReaders {

  private def getNode(xml: NodeSeq): ParseResult[Node] =
    if (xml.length == 0) {
      ParseFailure(EmptyError())
    } else if (xml.length == 1) {
      ParseSuccess(xml.head)
    } else {
      ParseFailure(MultipleMatchesError())
    }

  /**
   * [[XmlReader]] that matches exactly one XML node.
   *
   * If the input NodeSeq is empty, return `ParseFailure(EmptyError())`,
   * If the input NodeSeq contains multiple nodes return `ParseFailure(MultipleMatchesError())`.
   */
  implicit val nodeReader: XmlReader[Node] = XmlReader(getNode)

  /**
   * [[XmlReader]] matches the text of a single node.
   */
  implicit val stringReader: XmlReader[String] = XmlReader { xml =>
    getNode(xml).map(_.text)
  }

  /**
   * [[XmlReader]] that gets the text of a single node as a double.
   */
  implicit val doubleReader: XmlReader[Double] = XmlReader { xml =>
    getNode(xml).flatMap { node =>
      try {
        ParseSuccess(node.text.toDouble)
      } catch {
        case _: NumberFormatException => ParseFailure(TypeError(Double.getClass))
      }
    }
  }

  /**
   * [[XmlReader]] that gets the text of a single node as an int
   */
  implicit val intReader: XmlReader[Int] = XmlReader { xml =>
    getNode(xml).flatMap { node =>
      try {
        ParseSuccess(node.text.trim.toInt)
      } catch {
        case _: NumberFormatException => ParseFailure(TypeError(Int.getClass))
      }
    }
  }

  /**
   * [[XmlReader]] that gets the text of a single node as a long
   */
  implicit val longReader: XmlReader[Long] = XmlReader { xml =>
    getNode(xml).flatMap { node =>
      try {
        ParseSuccess(node.text.trim.toLong)
      } catch {
        case _: NumberFormatException => ParseFailure(TypeError(Long.getClass))
      }
    }
  }

  /**
   *  [[XmlReader]] that gets the text of a single node as a boolean
   */
  implicit val booleanReader: XmlReader[Boolean] = XmlReader { xml =>
    getNode(xml).flatMap { node =>
      try {
        ParseSuccess(node.text.trim.toBoolean)
      } catch {
        case _: IllegalArgumentException => ParseFailure(TypeError(Boolean.getClass))
      }
    }
  }

  /**
   *  Implicit [[XmlReader]] for an Option.
   * This [[XmlReader]] will always succeed. If reader succeeds, the [[XmlReader]] will obtain a `Some`,
   * otherwise it will be None
   */
  implicit def optionReader[T](implicit reader: XmlReader[T]): XmlReader[Option[T]] = reader.optional

  /**
   * Identity [[XmlReader]] that just extracts the `NodeSeq` it is passed.
   */
  implicit val identityReader: XmlReader[NodeSeq] = XmlReader(ParseSuccess[NodeSeq])

  /**
   * [[XmlReader]] that extracts an enumeration value from a `NodeSeq`.
   * It attempts to match the text of the node to the string value of one of the enumeration values.
   */
  implicit def enum[T <: Enumeration](e: T): XmlReader[e.Value] = stringReader.flatMap[e.Value]{ s =>
    e.values.find(_.toString == s).fold[XmlReader[e.Value]](fail(TypeError(e.getClass))) { v =>
      pure(v)
    }
  }

  /**
   * Wrap a value in an [[XmlReader]].
   * @return An [[XmlReader]] that always succeeds with `a`
   */
  def pure[A](a: => A) = XmlReader.algebra.pure(a)

  /**
   * @return An [[XmlReader]] that always fails with `error`
   */
  def fail(error: ParseError) = XmlReader[Nothing](_ => ParseFailure(error))

  /**
   * Read each node in the NodeSeq with reader, and succeeds with a [[PartialParseSuccess]] if
   * any of the elements fails.
   *
   * Use strictReadSeq if you want to fail on nodes that don't parse
   * @param reader
   * @tparam A
   * @return
   */
  def seq[A](implicit reader: XmlReader[A]): XmlReader[Seq[A]] =  XmlReader { xml =>
    ParseResult.combine(xml.map(reader.read))
  }

  /**
   *  Like [[seq]] but fail if any nodes fail the reader.
   */
  def strictReadSeq[A](implicit reader: XmlReader[A]): XmlReader[Seq[A]] = XmlReader { xml =>
    xml.reverse.foldLeft[ParseResult[List[A]]](ParseSuccess[List[A]](Nil)){ (result, node) =>
      for (l <- result; v <- reader.read(node)) yield v :: l
    }
  }

  /**
   * An [[XmlReader]] that succeeds if any of the supplied readers succeeds
   * with the input.
   */
  def any[T](reader0: XmlReader[T], readers: XmlReader[T]*) =
    readers.fold(reader0)(_ orElse _)

  /**
   *  An [[XmlReader]] that extracts the nth node of the NodeSeq
   */
  def nth[T](n: Int)(implicit reader: XmlReader[T]) = XmlReader { xml =>
    if (n >= xml.length) {
      ParseFailure(EmptyError())
    } else {
      reader.read(xml(n))
    }
  }

  /**
   * An [[XmlReader]] that extracts the first node of the NodeSeq
   */
  def first[T](implicit reader: XmlReader[T]) = nth[T](0)(reader)

  /**
   * An [[XmlReader]] that extracts a value from the attribute of the input NodeSeq.
   */
  def attribute[A](name: String)(implicit reader: XmlReader[A]) = XmlReader { xml =>
    val attrPath = s"@$name"
    (xml \ attrPath).headOption.fold[ParseResult[A]](ParseFailure(EmptyError(XPath \ attrPath)))(reader.read)
  }

  /**
   * An [[XmlReader]] that extracts Nodes with a tag label of `name` and then
   * applies `reader`.
   */
  def label[A](name: String)(implicit reader: XmlReader[A]) =
    nodeReader
      .filter(MismatchedLabelError(name))(_.label == name)
      .andThen(reader)

  /**
   * An [[XmlReader]] for extracting space delimited lists of values as an Array of strings.
   */
  val spaceDelimitedArray: XmlReader[Array[String]] = XmlReader { xml =>
    ParseSuccess(xml.text.split("\\s+"))
  }

  private def addPath(path: XPath, parseError: ParseError): ParseError = {
    parseError match {
      case pathError: PathError => pathError.extendPath(path)
      case other: ParseError => other
    }
  }

  /**
   * An [[XmlReader]] that applies `reader`` to the `NodeSeq` located at the [[XPath]] `path`.
   */
  def at[A](path: XPath)(implicit reader: XmlReader[A]) = XmlReader { xml =>
    reader.read(path(xml)) match {
      case PartialParseSuccess(get, errors) => PartialParseSuccess(get, errors.map(error => addPath(path, error)))
      case ParseFailure(errors) => ParseFailure(errors.map(error => addPath(path, error)))
      case other: ParseResult[A] => other
    }
  }
}
