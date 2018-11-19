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
      <d1>d1</d1>
      <d2>d2</d2>
      <e1>e1</e1>
      <e3>e3</e3>
    </doc>
    """)


  implicit def listXmlReader[A](implicit r: XmlReader[A]): XmlReader[List[A]] = XmlReader { xml =>
    ParseResult.combine(xml.map(r.read))
  }

  case class ABC(a: String, b: String, c: String, ds:List[String],es:List[String])

  "xtract syntax" should {
    "combine readers" in {
      val reader = (
        (__ \ "a").read[String],
        (__ \ "b").read[String],
        (__ \ "c").read[String],
        (__ \\? "d.".r).read[List[String]],
        (__ \? "e.".r).read[List[String]]
      ).mapN(ABC.apply _)

      reader.read(sample1) must beParseSuccess(ABC("A", "B", "C", List("d1","d2"), List("e1", "e3")))
    }

    "work with alternatives" in {
      val reader = (__ \ "opt1").read[String] | (__ \ "opt2").read[String]
      reader.read(XML.loadString("<a><opt2>Foo</opt2></a>")) must beParseSuccess("Foo")
    }
  }
}
