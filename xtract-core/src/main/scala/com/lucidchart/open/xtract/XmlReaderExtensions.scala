package com.lucidchart.open.xtract

trait XmlReaderExtensions
  extends IterableReaderExtensions
  with DoubleReaderExtensions

trait IterableReaderExtensions {
  // this apparently doesn't work as an implicit definition
  // if you need another implicit iterable class type just
  // extend this class and as seen in SeqReaderExtension
  class IterableReaderExtension[T, I <: Iterable[T]](iterableReader: XmlReader[I]) {
    def atLeast(count: Int) = iterableReader
      .filter(MinCountError(count))(_.size >= count)
  }

  implicit class SeqReaderExtension[T](seqReader: XmlReader[Seq[T]])
    extends IterableReaderExtension[T, Seq[T]](seqReader)
}

trait DoubleReaderExtensions {
  implicit class DoubleReaderExtension(doubleReader: XmlReader[Double]) {
    def inRange(min: Double, max: Double) = doubleReader
      .filter(RangeError(min, max)) { value =>
        min <= value && value <= max
      }
  }
}
