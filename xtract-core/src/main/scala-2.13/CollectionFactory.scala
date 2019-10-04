package com.lucidchart.open.xtract

import scala.collection.Factory
import scala.collection.mutable.Builder


/**
 * Shim for compatibility between scala 2.11/2.12 and 2.13
 */
class CollectionFactory[-A, +C](private val f: Factory[A,C]) extends AnyVal {
  def newBuilder: Builder[A, C] = f.newBuilder
}

object CollectionFactory {
  implicit def factory[A, C](implicit f: Factory[A, C]): CollectionFactory[A, C] = new CollectionFactory(f)
}
