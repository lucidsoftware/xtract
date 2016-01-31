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

  implicit val nodeReader: XmlReader[Node] = XmlReader(getNode)

  implicit val stringReader: XmlReader[String] = XmlReader { xml =>
    getNode(xml).map(_.text)
  }

  implicit val doubleReader: XmlReader[Double] = XmlReader { xml =>
    getNode(xml).flatMap { node =>
      try {
        ParseSuccess(node.text.toDouble)
      } catch {
        case _: NumberFormatException => ParseFailure(TypeError(Double.getClass))
      }
    }
  }

  implicit val intReader: XmlReader[Int] = XmlReader { xml =>
    getNode(xml).flatMap { node =>
      try {
        ParseSuccess(node.text.toInt)
      } catch {
        case _: NumberFormatException => ParseFailure(TypeError(Int.getClass))
      }
    }
  }

  implicit val booleanReader: XmlReader[Boolean] = XmlReader { xml =>
    getNode(xml).flatMap { node =>
      try {
        ParseSuccess(node.text.toBoolean)
      } catch {
        case _: IllegalArgumentException => ParseFailure(TypeError(Boolean.getClass))
      }
    }
  }

  implicit def optionReader[T](implicit reader: XmlReader[T]): XmlReader[Option[T]] = reader.optional

  implicit val identityReader: XmlReader[NodeSeq] = XmlReader(ParseSuccess[NodeSeq])

  implicit def enum[T <: Enumeration](e: T): XmlReader[e.Value] = stringReader.flatMap[e.Value]{ s =>
    e.values.find(_.toString == s).fold[XmlReader[e.Value]](fail(TypeError(e.getClass))) { v =>
      pure(v)
    }
  }

  def pure[A](a: => A) = XmlReader.applicative.pure(a)

  def fail(error: ParseError) = XmlReader[Nothing](_ => ParseFailure(error))

  /**
   * Read each node in the NodeSeq with reader, and succeeds with a PartialSuccess if
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

  def strictReadSeq[A](implicit reader: XmlReader[A]): XmlReader[Seq[A]] = XmlReader { xml =>
    xml.reverse.foldLeft[ParseResult[List[A]]](ParseSuccess[List[A]](Nil)){ (result, node) =>
      for (l <- result; v <- reader.read(node)) yield v :: l
    }
  }

  def any[T](reader0: XmlReader[T], readers: XmlReader[T]*) =
    readers.fold(reader0)(_ orElse _)

  def nth[T](n: Int)(implicit reader: XmlReader[T]) = XmlReader { xml =>
    if (n >= xml.length) {
      ParseFailure(EmptyError())
    } else {
      reader.read(xml(n))
    }
  }

  def first[T](implicit reader: XmlReader[T]) = nth[T](0)(reader)

  def attribute[A](name: String)(implicit reader: XmlReader[A]) = XmlReader { xml =>
    val attrPath = s"@$name"
    (xml \ attrPath).headOption.fold[ParseResult[A]](ParseFailure(EmptyError(XPath \ attrPath)))(reader.read)
  }

  def label[A](name: String)(implicit reader: XmlReader[A]) =
    nodeReader
      .filter(MismatchedLabelError(name))(_.label == name)
      .andThen(reader)

  val spaceDelimitedArray: XmlReader[Array[String]] = XmlReader { xml =>
    ParseSuccess(xml.text.split("\\s+"))
  }

  private def addPath(path: XPath, parseError: ParseError): ParseError = {
    parseError match {
      case pathError: PathError => pathError.extendPath(path)
      case other: ParseError => other
    }
  }

  def at[A](path: XPath)(implicit reader: XmlReader[A]) = XmlReader { xml =>
    reader.read(path(xml)) match {
      case PartialParseSuccess(get, errors) => PartialParseSuccess(get, errors.map(error => addPath(path, error)))
      case ParseFailure(errors) => ParseFailure(errors.map(error => addPath(path, error)))
      case other: ParseResult[A] => other
    }
  }
}
