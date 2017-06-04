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

case class AttributedXPathNode(attr: String, value: Option[String]) extends XPathNode {
  def apply(xml: NodeSeq): NodeSeq = xml.filter{ node =>
    node.attribute(attr) match {
      case Some(attrValues) => {
        value.fold(true)(_ == attrValues.toString)
      }
      case None => false
    }
  }
  override def toString = {
    value match {
      case Some(v) => s"[$attr=$v]"
      case None => s"[$attr]"
    }
  }
}

/**
 * Class representing an xpath.
 * It can be applied to a NodeSeq to get
 * a NodeSeq located at that path.
 *
 * @param path A sequence of [[XPathNode]]s to recursively
 * walk down the XML tree to the location of the path.
 */
case class XPath(path: List[XPathNode] = Nil) {
  /**
   * Equivalent of "/child" in xpath syntax.
   * @param child The name of the label of the child(ren).
   * @return a new [[XPath]] pointing to all children of this [[XPath]]
   *   with the given tag label.
   */
  def \(child: String) = XPath(path :+ KeyXPathNode(child))
  /**
   * Equivalent of "//child" in xpath.
   * @param child The name of the label of the descendents.
   * @return a new [[XPath]] that selects all descendents with
   *   the given tag label.
   */
  def \\(child: String) = XPath(path :+ RecursiveXPathNode(child))
  /**
   * Equivalent of "@attribute" in xpath.
   * @param attribute The name of the attribute to select
   * @return a new [[XPath]] that selects the attribute node with
   *   the given name
   */
  def \@(attribute: String) = XPath(path :+ KeyXPathNode("@" + attribute))
  /**
   * Concatenate two [[XPath]]s together
   */
  def ++(other: XPath) = XPath(path ++ other.path)
  /**
   * Equivalent of "[idx]" in xpath syntax.
   * @param idx The index of the node to select.
   * @return a new [[XPath]] that selects the node at index `idx` in the current selection.
   */
  def apply(idx: Int) = new XPath(path :+ IdxXPathNode(idx))

  /**
   * Equivalent of "[attribute]" or "[attribute=value]" in xpath syntax
   * @param name The name of the attribute to filter by
   * @param value If supplied filter to only nodes which have this value for the named attribute
   * @return a new [[XPath]] that selects only nodes which have an attribute with the given name, and
   * optionally the supplied value.
   */
  def with_attr(name: String, value: Option[String] = None): XPath = new XPath(path :+ AttributedXPathNode(name, value))

  /**
   * Equivalent to `with_attr(name, Some(value))`
   */
  def with_attr(name: String, value: String): XPath = with_attr(name, Some(value))

  /**
   * Equivalent of "[attribute=value]" in xpath syntax.
   * @param attr The name of the attribute to filter by
   * @param value The value of the attribute to filter by
   * @return a new [[XPath]] that selects only nodes which have the
   * given value for the given attribute.
   */
  def apply(attr: String, value: String) = with_attr(attr, Some(value))

  /**
   * Equivalent of "[attribute]" in xpath syntax.
   * @param attr The name of the attribute to filter by
   * @return a new [[XPath]] that selects only nodes which have the
   * given attribute.
   */
  def apply(attr: String) = with_attr(attr, None)


  /**
   * Equivalent of "/ *" in xpath syntax.
   * @return a new [[XPath]] that selects all children of the current selection
   */
  def children: XPath = XPath(path :+ KeyXPathNode("_"))

  /**
   * Apply this xpath to a NodeSeq.
   *
   * @param xml The NodeSeq to apply the path to.
   * @return the NodeSeq of the node(s) selected by this xpath.
   */
  def apply(xml: NodeSeq): NodeSeq = path.foldLeft(xml){ (x, p) => p(x) }

  override def toString = path.mkString

  /**
   * Create an [[XmlReader]] that reads the node(s) located at this xpath.
   * @param reader The reader to use on the node at this path
   */
  def read[A](implicit reader: XmlReader[A]): XmlReader[A] = XmlReader.at[A](this)(reader)

  /**
   * The same as [[read]] but take the reader as a lazy argument so that it can be used in recursive
   * definitions.
   */
  def lazyRead[A](r: => XmlReader[A]): XmlReader[A] = XmlReader( xml => XmlReader.at[A](this)(r).read(xml))

  /**
   * Create an [[XmlReader]] that reads an attribute at the current path.
   * @param name the name of the attribute to read
   * @param reader The [[XmlReader]] to read the attribute with
   */
  def readAttribute[A](name: String)(implicit reader: XmlReader[A]): XmlReader[A] = XmlReader.attribute[A](name).compose(XmlReader.at[NodeSeq](this))

}

/**
 * The root [[XPath]] path.
 */
object XPath extends XPath(Nil) {
}
