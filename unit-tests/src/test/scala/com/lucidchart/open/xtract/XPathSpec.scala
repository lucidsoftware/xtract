package com.lucidchart.open.xtract

import org.specs2.mutable.Specification
import scala.xml._

class XPathSpec extends Specification {
  "XPath.with_attr" should {
    "Filter elements that contain attribute" in {
      val xml = <d><a b="foo" /><a b="d" /><a /><a c="c" /><a b="b" /></d>
      val path = (__ \ "a")("b")

      path(xml) must_== NodeSeq.fromSeq(Seq(
        <a b="foo" />,
        <a b="d" />,
        <a b="b" />
      ))
    }

    "Filter elements by attribute name and value" in {
      val xml = <d><a t="5" n="f" /><a t="6" /><a y="5" /><a t="5" /><a /></d>
      val path = (__ \ "a")("t", "5")

      path(xml) must_== NodeSeq.fromSeq(Seq(
        <a t="5" n="f" />,
        <a t="5" />
      ))
    }
  }
}
