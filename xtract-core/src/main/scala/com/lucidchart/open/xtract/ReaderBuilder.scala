package com.lucidchart.open.xtract

import scala.reflect.macros.blackbox.Context

/**
 * A `ReadLine` can be used to specify the paths that should be used
 * to parse a field with an XmlReader defined with the `XmlReader.makeReader`
 * macro.
 */
case class ReadLine[+A] private (
  name: String,
  path: Option[XPath]
)

object ReadLine {
  def apply[A](name: String, path: XPath): ReadLine[A] = ReadLine[A](name, Some(path))
  def apply[A](name: String): ReadLine[A] = ReadLine[A](name, None)
}

/**
 * A helper class for the `XmlReader.makeReader` macro.
 */
private[xtract] class ReaderBuilder(val c: Context) {
  import c.universe._

  private case class MetaReaderLine(
    name: String,
    path: Option[Expr[XPath]],
    tpe: Type
  )

  private def makeReader[A: c.WeakTypeTag](params: Seq[MetaReaderLine]): c.Expr[XmlReader[A]] = {
    val tp = weakTypeOf[A]
    if (tp == typeOf[Nothing]) {
      c.abort(c.enclosingPosition, "You must specify a type parameter [?]")
    }

    val lines = params.map { line =>
      val term = TermName(line.name)

      val nodeExpr = line.path.map { path =>
        q"($path)(xml)"
      }.getOrElse {
        q"xml \ ${line.name}"
      }

      fq"$term <- com.lucidchart.open.xtract.XmlReader.of[${line.tpe}].read($nodeExpr)"
    }

    val names = params.map { line =>
      TermName(line.name)
    }

    val comp = weakTypeOf[A].typeSymbol
    val readImpl = q"""
      for(..$lines) yield new ${tp}(..$names)
    """

    val res = c.Expr[XmlReader[A]](q"""
      new com.lucidchart.open.xtract.XmlReader[$tp] {
        def read(xml: scala.xml.NodeSeq) = {
          $readImpl
        }
      }
    """)
    println(s"Result: $res")
    res
  }

  private def extractParams(fields: Seq[c.Expr[ReadLine[_]]]): Seq[MetaReaderLine] = {
    fields.map { lineExpr =>
      lineExpr.tree match {
        case current @ q"$expr[$tpe](...$argss)" => {
          val t: Type = tpe.asInstanceOf[TypeTree].tpe
          if (t == typeOf[Nothing]) {
            c.abort(current.pos, "You must specify a type parameter: ReadLine[?]")
          }
          argss.head match {
            case List(nameLit) => MetaReaderLine(extractLiteralString(nameLit), None, t)
            case List(nameLit, pathExpr) => MetaReaderLine(extractLiteralString(nameLit), Some(c.Expr(pathExpr)), t)
          }
        }
      }
    }
  }

  private def inferParams[A: c.WeakTypeTag]: Seq[MetaReaderLine] = {
    val tp = weakTypeOf[A]
    val params = tp.decl(termNames.CONSTRUCTOR).alternatives.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m.paramLists.flatten
    }.getOrElse(c.abort(c.enclosingPosition, s"No constructor found for ${tp}"))
    for (param <- params ) yield {
      MetaReaderLine(param.name.toString, None, param.info)
    }
  }

  private def extractLiteralString(t: Tree): String = {
    t match {
      case Literal(Constant(str: String)) => str
      case _ => c.abort(t.pos, "A literal String is required for the field name")
    }
  }

  def buildReader[A: c.WeakTypeTag](lines: c.Expr[ReadLine[_]]*): c.Expr[XmlReader[A]] = {
    val params: Seq[MetaReaderLine] = extractParams(lines)
    makeReader[A](params)
  }

  def inferReader[A: c.WeakTypeTag]: c.Expr[XmlReader[A]] = {
    val params = inferParams[A]
    makeReader[A](params)
  }

}
