package com.lucidchart.open.xtract

import scala.xml.{Node, NodeSeq}

sealed trait XPathNode extends Function[NodeSeq, NodeSeq]

case class IdxXPathNode(idx: Int) extends XPathNode {
  def apply(xml: NodeSeq): NodeSeq = xml(idx)
  override def toString = s"[$idx]"
}

case class KeyXPathNode(key: String) extends XPathNode {
  def apply(xml: NodeSeq): NodeSeq = xml \ key
  override def toString = s"/$key"
}

case class RecursiveXPathNode(key: String) extends XPathNode {
  def apply(xml: NodeSeq): NodeSeq = xml \\ key
  override def toString = s"//$key"
}

case class XPath(path: List[XPathNode] = Nil) {

  def \(child: String) = XPath(path :+ KeyXPathNode(child))
  def \\(child: String) = XPath(path :+ RecursiveXPathNode(child))
  def \@(attribute: String) = XPath(path :+ KeyXPathNode("@" + attribute))
  def ++(other: XPath) = XPath(path ++ other.path)
  def apply(idx: Int) = new XPath(path :+ IdxXPathNode(idx))

  def children: XPath = XPath(path :+ KeyXPathNode("_"))

  def apply(xml: NodeSeq): NodeSeq = path.foldLeft(xml){ (x, p) => p(x) }

  override def toString = path.mkString

  def read[A](implicit reader: XmlReader[A]): XmlReader[A] = XmlReader.at[A](this)(reader)

  def lazyRead[A](r: => XmlReader[A]): XmlReader[A] = XmlReader( xml => XmlReader.at[A](this)(r).read(xml))

  def readAttribute[A](name: String)(implicit reader: XmlReader[A]): XmlReader[A] = XmlReader.attribute[A](name).compose(XmlReader.at[NodeSeq](this))

}

object XPath extends XPath(Nil) {
}
