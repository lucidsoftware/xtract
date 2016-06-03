# xtract

Xtract is a scala library for deserializing XML. It is heavily inspired by the combinators in the [Play JSON library][1], in particular the [`Reads[T]`][2] class.

## Usage

To use Xtract in your sbt project add the following dependency:

``` scala
"com.lucidchart" %% "xtract" % "1.1.1"
```

There is also an `xtract-testing` artifact which provides helpful matchers and other functions for use with
specs2. To use in your test you can add the following sbt dependency:

``` scala
"com.lucidchart" %% "xtract-testing" % "1.0.1" % "test"
```

## Documentation

The scaladoc API for the core functionality is available at http://lucidsoftware.github.io/xtract/core/api/.

Scaladocs for the specs2 extensions is available at http://lucidsoftware.github.io/xtract/testing/api/.

[1]: https://www.playframework.com/documentation/2.5.x/ScalaJsonCombinators
[2]: https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.libs.json.Reads
