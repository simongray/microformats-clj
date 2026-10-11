Notes for html-pieces
=====================

What microformats-clj needed from html-pieces while parsing whole pages,
what it did instead, and what html-pieces could offer. html-pieces has
since taken on the URL resolver and the serializer, on its master at
`eda80a9`, which microformats-clj now depends on.


1. URL resolution
-----------------

**Done.** html-pieces has the namespace `dk.simongray.html-pieces.url`,
with the `resolve` and `resolver` that microformats-clj wrote, and a
`:base-url` option for `hiccup`, `sanitize`, `text` and `markdown`.
microformats-clj resolves its URLs with `url/resolver`, and its own
namespace is gone.


2. Whole pages
--------------

**Needed.** The parser reads whole pages, which the tree builder isn't
made for. In the suites of microformats/tests, one case gives a different
result:

- `microformats-v2-unit/implied/implied-url` has an `<a>` inside an
  `<a>`, inside a `<div>`. A browser takes it apart by the adoption agency
  algorithm and makes a copy of the outer `<a>` inside the `<div>`, so
  the page has two microformats of that type where html-pieces gives one.
  This changes three of its items.

No case depends on the repairs of tables. Two more things differ from a
browser on whole pages, though no case in the suites shows them:

- A `<template>` keeps its content as children, where a browser keeps it
  outside the document, so every walk of microformats-clj skips template
  elements itself.
- An element nested deeper than `:max-depth`, 128, is kept empty and its
  content goes to its parent. A property that deep would lose its text.

**Did.** The three items of `implied-url` are skipped by their type in the
tests, which the suite allows, since it names each top-level type once
per file. The parser reads pages with the default `:max-depth`.

**Could offer.** The adoption agency algorithm, by an option or a
namespace for whole pages, as the TODO of the tree builder says.


3. Serializing unsanitized HTML
-------------------------------

**Done.** html-pieces has `serialize`, which writes HTML or Hiccup by 13.3
of the HTML Standard without sanitizing it. microformats-clj writes the
html of e- properties with it, and its own writer is gone. See 8 for a
case where the result differs from the page.


4. Comments
-----------

**Needed.** A browser's innerHTML keeps the comments of an e- property.
The `parse` of html-pieces leaves them out, so the html of
microformats-clj has none.

**Did.** Nothing. No case in the suites has a comment in an e- property.

**Could offer.** An option to keep comments in the tree of `parse`, e.g.
as a `:!--` element, for those who write the tree out again.


5. Text content
---------------

**Needed.** The values of p-, dt- and e- properties use the DOM's
`textContent`, without `<script>` and `<style>` and with each `<img>` as
its alt text or URL. That's not what `text` gives, which lays text out in
paragraphs, lines and list items.

**Did.** The function `dk.simongray.microformats.html/text` walks the tree
and appends each text, with a function for the text of an `<img>`.

**Could offer.** Probably nothing: the rules for images are those of the
microformats spec.


6. Reading the tree
-------------------

**Needed.** The parts of an element, its children, and the ASCII
whitespace that HTML strips and splits on. They're in the `tree` and
`whitespace` namespaces of html-pieces, which aren't public. On the JVM,
`reduce` over the `subvec` of an element's children goes through an
iterator, which made the walks of a page noticeably slower than `nth`.

**Did.** The namespace `dk.simongray.microformats.html` has `element?`,
`tag`, `attrs`, `children`, `reduce-children`, `strip` and
`split-whitespace` of its own. The `parse` of html-pieces now reads built
Hiccup into the shape of parsed HTML, but it copies every element to do
so, also of Hiccup that `parse` gave. So microformats-clj takes the Hiccup
of `parse` as it is, and its README has you read built Hiccup with
`parse` first.

**Could offer.** `element?`, `parts` and a reduce over the children by
index, as public functions.


7. Keywords in ClojureScript
----------------------------

**Needed.** Fast lookups in the attribute maps of a large tree, e.g.
`(:class attrs)` for each element.

**Did.** Nothing. In Node, the parser takes about twice the share of the
parse time that it takes on the JVM.

**Could offer.** Untested: the keys of attribute maps are keywords that
html-pieces makes at run time, so in ClojureScript they aren't identical
to the keyword literals of the code that reads them, and each lookup
compares their names. If html-pieces put the literal keywords of the
common tag and attribute names in its cache of keywords first, a lookup
by a literal could succeed by `identical?`.


8. Writing parsed Hiccup
------------------------

**Needed.** The html of an e- property as the page has it.

**Did.** microformats-clj gives `serialize` the parsed Hiccup of the
property. `serialize` reads it again as built Hiccup, so it reads a tag
with a dot or a hash as shorthand: `<x-a.b>t</x-a.b>` comes out as
`<x-a class="b">t</x-a>`. HTML allows a dot in the name of a custom
element.

**Could offer.** A way to write parsed Hiccup as it is, e.g. by reading
shorthand only in Hiccup that `parse` didn't give.
