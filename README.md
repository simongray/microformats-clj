microformats-clj
================

[![Clojars Project](https://img.shields.io/clojars/v/dk.simongray/microformats-clj.svg)](https://clojars.org/dk.simongray/microformats-clj)

This is a [microformats2](https://microformats.org/wiki/microformats2)
parser for Clojure and ClojureScript. It parses the h-entries, h-cards and
other microformats on a page, along with its rel links, following the
[microformats2 parsing specification](https://microformats.org/wiki/microformats2-parsing).

- **To the spec.** It passes the
  [microformats test suite](https://github.com/microformats/tests), which
  covers the value-class-pattern, implied properties, nested
  microformats, rels and the microformats1 classes.
- **One tree.** It parses HTML with
  [html-pieces](https://github.com/simongray/html-pieces), or accepts the
  Hiccup that html-pieces produces, so you can parse a page once and use
  the tree for other things too.
- **Plain data.** The result follows the spec's JSON format, with
  keywords for most keys.

The same code runs on the JVM, in Node and in the browser.

> Like [html-pieces](https://github.com/simongray/html-pieces), it was
> developed with assistance from frontier LLMs.

Getting started
---------------

It requires Clojure 1.11+ and Java 11+. Add the dependency from
[Clojars](https://clojars.org/dk.simongray/microformats-clj) to your
`deps.edn`:

```clojure
dk.simongray/microformats-clj {:mvn/version "0.3.0"}
```

Parsing a page
--------------

Call `mf/parse` with the HTML of a page and its URL, so that the parser
can resolve relative URLs:

```clojure
(require '[dk.simongray.microformats :as mf])

(def page
  (str "<link rel=\"webmention\" href=\"/webmention\">"
       "<article class=\"h-entry\">"
       "<h1 class=\"p-name\">Hello</h1>"
       "<a class=\"p-author h-card\" href=\"/\">Jane</a>"
       "<time class=\"dt-published\" datetime=\"2026-10-10\">today</time>"
       "<div class=\"e-content\"><p>Hi <a href=\"/about\">there</a></p></div>"
       "</article>"))

(mf/parse page "https://jane.example/posts/hello")
;; => {:items    [{:type       ["h-entry"]
;;                 :properties {:name      ["Hello"]
;;                              :author    [{:type       ["h-card"]
;;                                           :properties {:name ["Jane"]
;;                                                        :url  ["https://jane.example/"]}
;;                                           :value      "Jane"}]
;;                              :published ["2026-10-10"]
;;                              :content   [{:html   "<p>Hi <a href=\"https://jane.example/about\">there</a></p>"
;;                                           :value  "Hi there"
;;                                           :hiccup ([:p {} "Hi " [:a {:href "https://jane.example/about"} "there"]])}]}}]
;;     :rels     {"webmention" ["https://jane.example/webmention"]}
;;     :rel-urls {"https://jane.example/webmention" {:rels ["webmention"]}}}
```

If you only need the rels of a page, e.g. its rel=me links, `mf/rels`
returns the same `:rels` and `:rel-urls` as `mf/parse` without parsing
the microformats.

Parsing Hiccup
--------------

If you need the page's tree for more than its microformats, e.g. to find
every link on it, parse it with html-pieces first and pass the Hiccup to
`mf/parse`:

```clojure
(require '[dk.simongray.html-pieces :as html])

(def tree (html/parse page))

(mf/parse tree "https://jane.example/posts/hello")
```

The Hiccup must be in the form that `html/parse` returns. If you've
written the Hiccup yourself, pass it through `html/parse` first to
normalize it.

The result
----------

The result is the spec's JSON format as Clojure data:

```clojure
(def result (mf/parse page "https://jane.example/posts/hello"))

(get-in result [:items 0 :properties :author 0 :properties :name])
;; => ["Jane"]

(get-in result [:rels "webmention"])
;; => ["https://jane.example/webmention"]
```

The keys are keywords, except for those in `:rels` and `:rel-urls`, which
are strings. A property name only ever contains lower-case letters,
digits and hyphens, so it works as a plain keyword. A rel value or a URL
can contain any character, but a slash in a keyword splits off a
namespace, e.g. `(name (keyword "http://a/b"))` returns `"/a/b"`, which
data.json would then write as the key.

Rel values are always lower case, since HTML compares them
case-insensitively, e.g. `rel="Webmention"` becomes the key
`"webmention"`.

Besides the spec's `:html` and `:value`, the value of an e- property
includes its content as `:hiccup`. You can pass that straight to
`html/hiccup` or `html/sanitize` to render the content safely without
parsing its HTML again.

URLs
----

The parser resolves relative URLs against the page's first `<base>`, if
it has one, or else against the page's URL. It uses
[whatwg-url](https://github.com/simongray/whatwg-url), which implements
the [URL Standard](https://url.spec.whatwg.org/), so URLs are normalized
the way a browser normalizes them. An empty `href`, e.g. on a Webmention
endpoint, therefore resolves to the page itself, or to its `<base>`.

When the page's URL is nil and there's no `<base>`, relative URLs stay as
they are.

Text values
-----------

A text value, e.g. of a p- property or the `:value` of an e- property, is
the element's text content, as in the spec, but with a line break for
each `<br>`. A block element such as `<p>` or `<li>` also starts and ends
with a line break wherever two words would otherwise run together.

Whitespace is left as it is on the page, which is what the test suite
expects. To collapse it instead, as php-mf2 and mf2py do (following a
[draft](https://microformats.org/wiki/textcontent-parsing)), pass an
option:

```clojure
(mf/parse "<p class=\"h-card\">Jane\n   Doe</p>" nil)
;; => {:items [{:type ["h-card"] :properties {:name ["Jane\n   Doe"]}}] …}

(mf/parse "<p class=\"h-card\">Jane\n   Doe</p>" nil {:collapse-whitespace? true})
;; => {:items [{:type ["h-card"] :properties {:name ["Jane Doe"]}}] …}
```

Microformats1 classes
---------------------

The parser reads microformats1 and hNews classes such as `vcard` and
`hentry` as the spec's backcompat microformats, e.g. h-card and h-entry,
using the mappings on the microformats wiki. As the spec requires, a
backcompat microformat has no implied name, photo or url. It can include
properties from elsewhere on the page with the
[include pattern](https://microformats.org/wiki/include-pattern).

The [h-entry](https://microformats.org/wiki/h-entry#Parser_Compatibility)
page also proposes two rules that haven't been accepted:

- An hentry without a published date gets the `datetime` of its first
  `<time class="entry-date">`, as in the default WordPress themes from
  2011 to 2014.
- An hentry without an author gets the URL of its first `rel="author"`
  link.

To apply these rules, pass `{:proposed-backcompat? true}` as the options
to `mf/parse`.

Reading the result
------------------

These functions take the result of `mf/parse`:

- `mf/items` returns every microformat on the page in document order,
  nested ones included, or only those of one type.
- `mf/property` returns the first non-empty value of a property as text,
  whether that value is a string or an embedded microformat.
- `mf/value` returns the text of a single value.
- `mf/urls` returns the URLs of a property, including those of an
  embedded microformat such as an h-cite, normalized so that you can
  compare them.

```clojure
(def entry (first (mf/items result "h-entry")))

(mf/property entry :author)
;; => "Jane"
```

Authorship
----------

To find the author of an h-entry, `mf/author` follows the
[authorship spec](https://indieweb.org/authorship-spec):

```clojure
(mf/author result "https://jane.example/posts/hello" entry)
;; => {:card {:type       ["h-card"]
;;            :properties {:name ["Jane"] :url ["https://jane.example/"]}
;;            :value      "Jane"}}
```

An entry can also name its author by just the URL of their page, in which
case `mf/author` returns that URL as `:page`. The library doesn't fetch
anything, so fetch and parse that page yourself, then call
`mf/author-card` with the result to find the author's h-card there, or
failing that on the entry's page.

To find the
[representative h-card](https://microformats.org/wiki/representative-h-card-parsing)
of a page, use `mf/representative-card`.

Principles
----------

- **Standards first.** The parser follows the microformats2 parsing
  specification, and the URL Standard for URLs. The code cites the
  sections it implements.
- **The suite decides.** Where the spec is unclear, the parser does what
  the test suite expects. Where the suite contradicts itself, predates
  the spec, or depends on how a browser builds the tree for a whole page,
  the tests skip those cases with a comment saying why.
- **One walk.** The parser finds both class and rel microformats in a
  single walk of the tree, as the spec allows, except on a page that uses
  the microformats1 include pattern.
- **One codebase.** The library is written in `.cljc`. It depends on
  html-pieces to parse HTML and on whatwg-url to resolve URLs.

Performance
-----------

The [benchmarks](doc/benchmarks.md) show how much time the parser adds to
parsing a page with html-pieces, on the JVM and in Node.

Development
-----------

```bash
clojure -X:test                                        # the tests on the JVM
npm install                                            # once, for the Node tests
clojure -M:cljs compile test                           # the tests in Node
clojure -X:bench                                       # the benchmarks on the JVM
clojure -M:cljs release bench && node target/bench.js  # the benchmarks in Node
```

The tests run the
[microformats/tests](https://github.com/microformats/tests) suites if
they're in `dev-resources/microformats-tests/`, which Git ignores. CI
clones them into that folder. To do the same locally, clone the suites
and check out the commit that the parser passes:

```bash
git clone https://github.com/microformats/tests dev-resources/microformats-tests
git -C dev-resources/microformats-tests checkout d49f5d76d0395676274c9ff15f1a14091498d7d4
```

The first time the JVM benchmarks run, they download their pages into
`bench/fixtures/`, which Git ignores. The Node benchmarks read the pages
from there.

License
-------

The microformats-clj project is licensed under the [MIT licence](LICENSE).
