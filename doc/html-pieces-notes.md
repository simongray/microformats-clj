Notes for html-pieces
=====================

What microformats-clj needed from html-pieces 0.2.0 while parsing whole
pages, what it did instead, and what html-pieces could offer.


1. URL resolution
-----------------

**Needed.** Every u- property, implied photo and url, rel, and URL in the
HTML of an e- property is resolved against the page URL and the first
`<base>`, and normalized as a browser does. This has to work in `.cljc`.
The html-pieces README has you call `java.net.URI` in `:url-fn`, which
only works on the JVM, and `URI.resolve` doesn't normalize as a browser
does, e.g. `HTTP://A:80/c/../d` stays as it is, where a browser gives
`http://a/d`.

**Did.** The namespace `dk.simongray.microformats.url` resolves by RFC
3986, section 5, with the changes of the WHATWG URL Standard. Its
`resolve` takes a base and a URL, and its `resolver` reads the base once
for the many URLs of a page. It has no dependency on the rest of
microformats-clj. Left out, as a TODO in
its docstring: the host parser (IDNA, IPv4 in other bases, IPv6
compression) and the drive letters of file URLs.

**Could offer.** The namespace as it is, and a `:base-url` option, so that
`hiccup` and `sanitize` keep relative URLs, resolved, where they now leave
them out. The README's `:url-fn` example would then need no
`java.net.URI`.


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

**Needed.** The html of an e- property is the element's innerHTML, by
13.3 of the HTML Standard. The serializer of html-pieces is for sanitized
Hiccup and isn't public. It escapes the text of `<script>` and `<style>`,
which 13.3 writes as it is, and writes a no-break space as itself rather
than as `&nbsp;`.

**Did.** The function `dk.simongray.microformats.html/html` follows 13.3,
with the raw-text elements and `&nbsp;`, and with `<` and `>` escaped in
attribute values, as the HTML Standard has done since 2025.

**Could offer.** A public function that writes any Hiccup as HTML by 13.3,
without sanitizing it, which `sanitize` would then be built on.


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
`split-whitespace` of its own. It takes only the Hiccup of `parse`, with
an optional attribute map.

**Could offer.** These as public functions. Since 0.2.0, html-pieces reads
built Hiccup, e.g. with seqs among the children, nil and shorthand tags,
by `tree/canonical`. Made public, it would let microformats-clj take built
Hiccup the same way.


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
