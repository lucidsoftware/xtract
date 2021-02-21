package com.lucidchart.open.xtract

import org.specs2.mutable.Specification
import scala.xml._

import com.lucidchart.open.xtract.meta.{makeReader, ReadParam}

class MacroSpec extends Specification with ParseResultMatchers {

  case class A(a: Int, b: String, c: Option[Double])
  object A {
    implicit val reader = makeReader[A]
  }

  case class B(a: Int, b: String, c: Option[Double])
  object B {
    implicit val reader = makeReader[B](
      ReadParam[Int]("a"),
      ReadParam[String]("b", __ \@ "b"),
      ReadParam[Option[Double]]("c", __ \ "c" \@ "value"),
    )
  }

  "makeReader" should {
    "create reader for case class with no arguments" in {
      val sample = XML.loadString("""
        <doc>
          <a>1</a>
          <b>foo</b>
        </doc>
        """)

      XmlReader.of[A].read(sample) must beParseSuccess(A(1, "foo", None))
    }

    "create reader with explicit parameters" in {
      val sample = XML.loadString("""
        <doc b="hi">
          <a>1</a>
          <c value="3.4" />
        </doc>
        """)

      XmlReader.of[B].read(sample) must beParseSuccess(B(1, "hi", Some(3.4)))
    }
  }
}


