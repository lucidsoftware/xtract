package com.lucidchart.open.xtract

import org.specs2.mutable.Specification

class ParseResultSpec extends Specification {

  case class FakeError() extends ParseError

  "ParseResult.filter" should {
    "return this when failure" in {
      val failure = ParseFailure(FakeError())
      failure.filter(_ => false) must beTheSameAs(failure)
    }

    "return this when filter succeeds" in {
      val success = ParseSuccess(20)
      success.filter(_ => true) must beTheSameAs(success)
    }

    "return failure when filter fails" in {
      ParseSuccess(20).filter(_ => false) must_== ParseFailure(Nil)
    }

    "filter method called with value" in {
      var value = 0
      ParseSuccess(20).filter { num =>
        value = num
        true
      }
      value must_== 20
    }

    "filter method with alternate error returns failure with the error" in {
      ParseSuccess(20).filter(FakeError())(_ => false) must_== ParseFailure(FakeError())
    }
  }

}
