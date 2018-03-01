package com.lucidchart.open.xtract

import org.specs2.matcher.{MatchResult, ValueCheck}
import scala.xml._

class DefaultXmlReadersSpec extends XmlReaderSpecification with DefaultXmlReaders {

  // Don't allow parallel execution, because that leads to a deadlock
  // See https://github.com/mockito/mockito/issues/1067
  sequential

  case class FakeParseError() extends ParseError

  val empty = NodeSeq.fromSeq(Nil)
  val multiple = NodeSeq.fromSeq(Seq(
    <xml>a</xml>,
    <xml>b</xml>,
    <xml>c</xml>
  ))

  "nodeReader" should {
    "parse node" in {
      nodeReader.read(<xml></xml>) must beParseSuccess({ node: Node =>
        node.label must_== "xml"
      })
    }

    "not parse no nodes" in {
      nodeReader.read(empty) must beParseFailure(Seq(
        EmptyError()
      ))
    }

    "not parse multiple nodes" in {
      nodeReader.read(multiple) must beParseFailure(Seq(
        MultipleMatchesError()
      ))
    }
  }

  "stringReader" should {
    "parse string from node" in {
      stringReader.read(<xml></xml>) must beParseSuccess("")
    }

    "not parse no nodes" in {
      stringReader.read(empty) must beParseFailure(Seq(
        EmptyError()
      ))
    }

    "not parse multiple nodes" in {
      stringReader.read(multiple) must beParseFailure(Seq(
        MultipleMatchesError()
      ))
    }
  }

  "doubleReader" should {
    "parse double from node" in {
      doubleReader.read(<xml>22.4</xml>) must beParseSuccess(22.4)
    }

    "give type error for bad format" in {
      doubleReader.read(<xml>abc</xml>) must beParseFailure(Seq(
        TypeError(Double.getClass)
      ))
    }

    "give empty error for missing node" in {
      doubleReader.read(empty) must beParseFailure(Seq(
        EmptyError()
      ))
    }

    "give multiple matches error for multiple matches" in {
      doubleReader.read(multiple) must beParseFailure(Seq(
        MultipleMatchesError()
      ))
    }
  }

  "intReader" should {
    "parse double from node" in {
      intReader.read(<xml>22</xml>) must beParseSuccess(22)
    }

    "give type error for bad format" in {
      intReader.read(<xml>abc</xml>) must beParseFailure(Seq(
        TypeError(Int.getClass)
      ))
    }

    "give empty error for missing node" in {
      intReader.read(empty) must beParseFailure(Seq(
        EmptyError()
      ))
    }

    "give multiple matches error for multiple matches" in {
      intReader.read(multiple) must beParseFailure(Seq(
        MultipleMatchesError()
      ))
    }
  }

  "booleanReader" should {
    "parse double from node" in {
      booleanReader.read(<xml>false</xml>) must beParseSuccess(false)
    }

    "give type error for bad format" in {
      booleanReader.read(<xml>abc</xml>) must beParseFailure(Seq(
        TypeError(Boolean.getClass)
      ))
    }

    "give empty error for missing node" in {
      booleanReader.read(empty) must beParseFailure(Seq(
        EmptyError()
      ))
    }

    "give multiple matches error for multiple matches" in {
      booleanReader.read(multiple) must beParseFailure(Seq(
        MultipleMatchesError()
      ))
    }
  }

  "label" should {
    "be failure when empty xml" in {
      label("blah")(pure(23)).read(empty) must beParseFailure(Seq(
        EmptyError()
      ))
    }

    "be failure when multiple matches" in {
      label("xml")(pure(23)).read(multiple) must beParseFailure(Seq(
        MultipleMatchesError()
      ))
    }

    "be failure when label doesn't match" in {
      label("xml")(pure(23)).read(<blah></blah>) must beParseFailure(Seq(
        MismatchedLabelError("xml")
      ))
    }

    "be success when label matches" in {
      label("xml")(pure(23)).read(<xml></xml>) must beParseSuccess(23)
    }
  }

  case class NumberError(num: Int) extends ParseError

  "any" should {
    "succeed: 1/3 succeed" in {
      any(fail(FakeParseError()), fail(FakeParseError()), pure(3))
        .read(<xml></xml>) must beParseSuccess(3)
    }

    "succeed: 1/1 succeed" in {
      any(pure(3))
        .read(<xml></xml>) must beParseSuccess(3)
    }

    "failure: 0/3 succeed" in {
      any(fail(NumberError(1)), fail(NumberError(2)), fail(NumberError(3)))
        .read(<xml></xml>) must beParseFailure(Seq(NumberError(3)))
    }

    "failure: 0/1 succeed" in {
      any(fail(NumberError(1)))
        .read(<xml></xml>) must beParseFailure(Seq(NumberError(1)))
    }
  }

  "at" should {

    "pass xml to path" in {
      val mockXPath = mock[XPath]
      mockXPath.apply(any[NodeSeq]) returns <xml></xml>
      val reader = successXmlReader[String]("abc")

      at(mockXPath)(reader).read(<blah></blah>)

      there was one(mockXPath).apply(argThat(isNodeWithLabel("blah")))
    }

    "read result from xpath" in {
      val mockXPath = mock[XPath]
      mockXPath.apply(any[NodeSeq]) returns <frompath></frompath>
      val reader = successXmlReader[String]("abc")

      at(mockXPath)(reader).read(<blah></blah>)

      there was one(reader).read(argThat(isNodeWithLabel("frompath")))
    }

    case class FakePathError(path: XPath = XPath) extends PathError {
      protected def withPath(newPath: XPath) = FakePathError(newPath)
    }

    "add path to PathErrors in PartialParseSuccess" in {
      val path = XPath \ "a" \ "b"
      val reader = partialSuccessXmlReader[String]("a", Seq(FakePathError(XPath \ "c")))

      at(path)(reader).read(<blah></blah>) must bePartialSuccess { partial =>
        partial.errors must_== Seq(FakePathError(XPath \ "a" \ "b" \ "c"))
      }
    }

    "add path to PathErrors in ParseFailure" in {
      val path = XPath \ "a" \ "b"
      val reader = failureXmlReader[String](FakePathError(XPath \ "c"))

      at(path)(reader).read(<blah></blah>) must beParseFailure(Seq(
        FakePathError(XPath \ "a" \ "b" \ "c")
      ))
    }

    "keep other errors intact" in {
      val path = XPath \ "a" \ "b"
      val reader = failureXmlReader[String](FakeParseError())

      at(path)(reader).read(<blah></blah>) must beParseFailure(Seq(
        FakeParseError()
      ))
    }
  }

  "seq" should {

    "parse an empty element gives Nil" in {
      seq(pure(1)).read(empty) must beParseSuccess({ result: Seq[Int] =>
        result must beEmpty
      })
    }

    "parse an empty element with failure parser should give Nil" in {
      seq(fail(FakeParseError())).read(empty) must beParseSuccess({ result: Seq[_] =>
        result must beEmpty
      })
    }

  }

  "nth" should {
    "retrieve nth node" in {
      nth[Node](2).read(NodeSeq.fromSeq(
        <xml>a</xml>
        <xml>b</xml>
        <xml>c</xml>
      )).map(_.text) must beParseSuccess("c")
    }

    "produce EmptyError if node doesn't exist" in {
      nth[Node](2).read(NodeSeq.fromSeq(
        <xml>a</xml>
        <xml>b</xml>
      )) must beParseFailure(Seq(
        EmptyError()
      ))
    }
  }

  "first" should {
    "retrieve first node" in {
      first[Node].read(NodeSeq.fromSeq(
        <xml>a</xml>
        <xml>b</xml>
      )).map(_.text) must beParseSuccess("a")
    }

    "produce EmptyError if empty list" in {
      first[Node].read(empty) must beParseFailure(Seq(EmptyError()))
    }
  }

}
