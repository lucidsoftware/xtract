# xtract
[![Build Status](https://travis-ci.com/lucidsoftware/xtract.svg?branch=master)](https://travis-ci.com/lucidsoftware/xtract)

Xtract is a scala library for deserializing XML. It is heavily inspired by the combinators in the [Play JSON library][1], in particular the [`Reads[T]`][2] class.

See the [introductory blog post](https://www.lucidchart.com/techblog/2016/07/12/introducing-xtract-a-new-xml-deserialization-library-for-scala/).

## Usage

To use Xtract in your sbt project add the [following dependency](https://mvnrepository.com/artifact/com.lucidchart/xtract):

``` scala
"com.lucidchart" %% "xtract" % "2.2.1"
```

There is also an `xtract-testing` artifact which provides helpful matchers and other functions for use with
specs2. To use in your test you can add the [following sbt dependency](https://mvnrepository.com/artifact/com.lucidchart/xtract-testing):

``` scala
"com.lucidchart" %% "xtract-testing" % "2.2.1" % "test"
```

## Documentation

The scaladoc API for the core functionality is available at http://lucidsoftware.github.io/xtract/core/api/com/lucidchart/open/xtract/index.html.

Scaladocs for the specs2 extensions is available at http://lucidsoftware.github.io/xtract/testing/api/com/lucidchart/open/xtract/index.html.

## Example

An example project using xtract can be found at http://github.com/lucidsoftware/xtract-example.

[1]: https://www.playframework.com/documentation/2.5.x/ScalaJsonCombinators
[2]: https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.libs.json.Reads
