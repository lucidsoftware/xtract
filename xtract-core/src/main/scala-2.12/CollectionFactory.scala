package com.lucidchart.open.xtract

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder

/**
 * Shim for compatibility between scala 2.11/2.12 and 2.13
 */
class CollectionFactory[-A, +C](private val cbf: CanBuildFrom[_, A, C]) extends AnyVal {
  def newBuilder: Builder[A, C] = cbf()
}

object CollectionFactory {
  implicit def factory[A, C](implicit cbf: CanBuildFrom[_, A, C]) = new CollectionFactory(cbf)
}
