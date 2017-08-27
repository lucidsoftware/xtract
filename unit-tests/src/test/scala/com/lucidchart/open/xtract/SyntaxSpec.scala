package com.lucidchart.open.xtract

import cats.syntax.all._
import org.specs2.mutable.Specification
import scala.xml._

import com.lucidchart.open.xtract.XmlReader._

class SyntaxSpec extends Specification with ParseResultMatchers {
  val sample1 = XML.loadString("""
    <doc>
      <a>A</a>
      <b>B</b>
      <c>C</c>
    </doc>
    """)

  case class ABC(a: String, b: String, c: String)

  "xtract syntax" should {
    "combine readers" in {
      val reader = (
        (__ \ "a").read[String],
        (__ \ "b").read[String],
        (__ \ "c").read[String]
      ).mapN(ABC.apply _)

      reader.read(sample1) must beParseSuccess(ABC("A", "B", "C"))
    }

    "work with alternatives" in {
      val reader = (__ \ "opt1").read[String] | (__ \ "opt2").read[String]
      reader.read(XML.loadString("<a><opt2>Foo</opt2></a>")) must beParseSuccess("Foo")
    }
  }
}
