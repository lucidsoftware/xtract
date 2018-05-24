# 2.0.0

The main change in this release is a switch from the play functional library to cats. There were several reasons
for this change, including that the cats library is more discoverable, and probably more familiar for most users.
Unfortunately, this change is not backwards compatible. And in particular the combinator syntax changed. 

For example, in version 1.x you may have used something like this:

```scala
 implicit val reader: XmlReader[Blog] = (
    (__ \ "head" \ "title").read[String] and
      (__ \ "head"\ "subtitle").read[String].optional and
      (__ \ "head" \ "author").read[AuthorInfo] and
      attribute("type")(enum(BlogType)).default(BlogType.tech) and
      (__ \ "body").read[Content]
  )(apply _)
```

In version 2.x this will now be:

```scala
 implicit val reader: XmlReader[Blog] = (
    (__ \ "head" \ "title").read[String],
      (__ \ "head"\ "subtitle").read[String].optional,
      (__ \ "head" \ "author").read[AuthorInfo],
      attribute("type")(enum(BlogType)).default(BlogType.tech),
      (__ \ "body").read[Content]
  ).mapN(apply _)
```

Other changes:
  - Several dependencies were updated
  - A new `xtract-macros` package was added that contains a macro to automatically generate `XmlReader`s from
  case classes
