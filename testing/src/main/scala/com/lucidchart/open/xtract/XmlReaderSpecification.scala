package com.lucidchart.open.xtract

import org.specs2.matcher._
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike

trait XmlReaderSpies extends Mockito {
  // anonymous classes are created with final, so reflection can't get a spy.
  // XmlReader.pure and XmlReader.fail don't work
  private class PureXmlReader[T](result: T) extends XmlReader[T] {
    def read(elem: scala.xml.NodeSeq): ParseResult[T] = ParseSuccess(result)
  }

  private class PartialSuccessXmlReader[T](result: T, errors: Seq[ParseError]) extends XmlReader[T] {
    def read(elem: scala.xml.NodeSeq): ParseResult[T] = PartialParseSuccess(result, errors)
  }

  private class FailureXmlReader[T](errors: Seq[ParseError] = Nil) extends XmlReader[T] {
    def read(elem: scala.xml.NodeSeq): ParseResult[T] = ParseFailure(errors)
  }

  def successXmlReader[T](result: T): XmlReader[T] =
    spy(new PureXmlReader(result))

  def partialSuccessXmlReader[T](result: T, errors: Seq[ParseError]): XmlReader[T] =
    spy(new PartialSuccessXmlReader[T](result, errors))

  def failureXmlReader[T]: XmlReader[T] =
    failureXmlReader[T](Nil)

  def failureXmlReader[T](error: ParseError): XmlReader[T] =
    failureXmlReader[T](Seq(error))

  def failureXmlReader[T](errors: Seq[ParseError]): XmlReader[T] =
    spy(new FailureXmlReader[T](errors))

  def xmlReader[T](maybeResult: Option[T]): XmlReader[T] =
    maybeResult.fold(failureXmlReader[T])(successXmlReader[T])
}

trait XmlMatchers extends SpecificationLike {
  def isNodeWithAttribute(attribute: String, expected: String): Matcher[scala.xml.NodeSeq] = { nodes: scala.xml.NodeSeq =>
    val nodeId = nodes.headOption.map(_ \@ attribute)
    (nodeId.exists(_ == expected),
      "Node ids matched",
      nodeId.fold(
        s"Node does not have '$attribute'"
      ) { actual =>
        s"Node's id Self '$actual' does not match expected '$expected'"
      })
  }

  def isNodeWithLabel(label: String): Matcher[scala.xml.NodeSeq] = { nodes: scala.xml.NodeSeq =>
    val node = nodes.headOption
    (node.exists(_.label == label),
      s"$nodes is a Node with label '$label'",
      s"$nodes is not a Node or doesn't match label '$label'")
  }

  def isNodeSeqWithLength(n: Int): Matcher[scala.xml.NodeSeq] = { nodes: scala.xml.NodeSeq =>
    (nodes.length == n,
      s"$n nodes found",
      s"${nodes.length} nodes found but $n nodes expected")
  }
}

trait ParseResultMatchers extends Matchers {
  private def toParseSuccessOption[T](result: ParseResult[T]): Option[T] = result match {
    case success: ParseSuccess[T] => Some(success.get)
    case _ => None
  }

  private class SuccessMatcher[T]
    extends OptionLikeMatcher[ParseResult,T,T]("ParseSuccess", toParseSuccessOption)

  private class SuccessCheckedMatcher[T](check: ValueCheck[T])
    extends OptionLikeCheckedMatcher[ParseResult,T,T]("ParseSuccess", toParseSuccessOption, check)

  def beParseSuccess[T]: Matcher[ParseResult[T]] =
    new SuccessMatcher[T]()

  def beParseSuccess[T](check: ValueCheck[T]): Matcher[ParseResult[T]] =
    new SuccessCheckedMatcher[T](check)



  private def toErrorsOption[T](result: ParseResult[T]): Option[Seq[ParseError]] = result match {
    case failure: ParseFailure => Some(failure.errors)
    case _ => None
  }

  private class FailureMatcher[T]
    extends OptionLikeMatcher[ParseResult,T,Seq[ParseError]]("ParseFailure", toErrorsOption)

  private class FailureCheckedMatcher[T](check: ValueCheck[Seq[ParseError]])
    extends OptionLikeCheckedMatcher[ParseResult,T,Seq[ParseError]]("ParseFailure", toErrorsOption, check)

  def beParseFailure[T]: Matcher[ParseResult[T]] = new FailureMatcher[T]()

  def beParseFailure[T](check: ValueCheck[Seq[ParseError]]): Matcher[ParseResult[T]] =
    new FailureCheckedMatcher[T](check)



  def bePartialSuccess[T](check: PartialParseSuccess[T] => MatchResult[Any]): Matcher[ParseResult[T]] = beLike {
    case partial: PartialParseSuccess[T] =>
      check(partial)
  }

}

trait XmlReaderSpecification
  extends XmlReaderSpies
  with XmlMatchers
  with ParseResultMatchers
