microformats-clj
================

[![Clojars Project](https://img.shields.io/clojars/v/dk.simongray/microformats-clj.svg)](https://clojars.org/dk.simongray/microformats-clj)

This is a [microformats2](https://microformats.org/wiki/microformats2)
parser for Clojure and ClojureScript. It reads the h-entries, h-cards and
other microformats of a page, and its rel links, as the
[microformats2 parsing specification](https://microformats.org/wiki/microformats2-parsing)
defines them.

- **To the spec.** It passes the
  [microformats test suite](https://github.com/microformats/tests), which
  covers the value-class-pattern, implied properties, nested
  microformats, rels and the classes of microformats1.
- **One tree.** It reads HTML with
  [html-pieces](https://github.com/simongray/html-pieces), or takes the
  Hiccup that html-pieces has already read, so you can parse a page once
  and use the tree for other things too.
- **Plain data.** The result has the shape of the spec's JSON, with
  keywords for most keys.

The same code runs on the JVM, in Node and in the browser.

> Like html-pieces, it was developed with assistance from frontier LLMs.

Getting started
---------------

It requires Clojure 1.11+ and Java 11+. Add it from
[Clojars](https://clojars.org/dk.simongray/microformats-clj) to the
`:deps` in your `deps.edn`:

```clojure
dk.simongray/microformats-clj {:mvn/version "0.2.0"}
```

Then parse a page, with the URL to resolve its relative URLs against:

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

If you need the tree of the page for more than its microformats, e.g. for
every link on it, parse it with html-pieces first and give `mf/parse` the
Hiccup:

```clojure
(require '[dk.simongray.html-pieces :as html])

(def tree (html/parse page))

(mf/parse tree "https://jane.example/posts/hello")
```

The Hiccup has to be in the shape that `html/parse` gives. For Hiccup
that you've built yourself, pass it through `html/parse` first, which
puts it in that shape.

The result
----------

The result is the JSON of the spec as Clojure data:

```clojure
(def result (mf/parse page "https://jane.example/posts/hello"))

(get-in result [:items 0 :properties :author 0 :properties :name])
;; => ["Jane"]

(get-in result [:rels "webmention"])
;; => ["https://jane.example/webmention"]
```

The keys are keywords, except those of `:rels` and `:rel-urls`, which are
strings. A property name is always made of lower-case letters, digits
and hyphens, so it makes a plain keyword. A rel value or a URL can hold
any character, and a slash in a keyword splits off a namespace, e.g.
`(name (keyword "http://a/b"))` is `"/a/b"`, which is also the key that
data.json would write. Rel values are in lower case, since HTML
compares them without regard to case, e.g. `rel="Webmention"` gives the
key `"webmention"`.

An e- property has its content as `:hiccup`, next to the `:html` and the
`:value` of the spec. You can pass it straight to `html/hiccup` or
`html/sanitize` to show the content without parsing its HTML again.

URLs are resolved against the page URL and the first `<base>`, and
normalized as a browser normalizes them, by the
[URL Standard](https://url.spec.whatwg.org/). So an empty `href`, e.g. of
a Webmention endpoint, gives the URL of the page itself, or of its
`<base>` when it has one. When the URL of the page is nil and there's no
`<base>`, relative URLs stay as they are.

A text value, e.g. of a p- property or the `:value` of an e- property, is
the text content of its element, as the spec has it, but with a line
break at each `<br>`. There's also one at the start and the end of a
block element such as `<p>` or `<li>`, where the text would otherwise run
two words together. The text keeps the whitespace of the page, as the
test suite expects. To collapse it, as php-mf2 and mf2py do, following a
[draft](https://microformats.org/wiki/textcontent-parsing), pass an
option:

```clojure
(mf/parse "<p class=\"h-card\">Jane\n   Doe</p>" nil)
;; => {:items [{:type ["h-card"] :properties {:name ["Jane\n   Doe"]}}] …}

(mf/parse "<p class=\"h-card\">Jane\n   Doe</p>" nil {:collapse-whitespace? true})
;; => {:items [{:type ["h-card"] :properties {:name ["Jane Doe"]}}] …}
```

The classes of microformats1 and hNews, e.g. `vcard` and `hentry`, are
read as the spec's backcompat microformats, e.g. h-card and h-entry, by
the mappings on the microformats wiki. As the spec has it, a backcompat
microformat implies no name, photo or url. It can take properties from
elsewhere on the page by the
[include pattern](https://microformats.org/wiki/include-pattern).

The [h-entry](https://microformats.org/wiki/h-entry#Parser_Compatibility)
page also proposes two rules that haven't been accepted: an hentry
without a published date takes the `datetime` of its first
`<time class="entry-date">`, as in the default WordPress themes from
2011 to 2014, and one without an author takes the URL of its first
`rel="author"` link. To apply them, pass `{:proposed-backcompat? true}`
as the options of `mf/parse`.

Reading the result
------------------

A few functions read the result of `mf/parse`:

- `mf/items` gives every microformat of the page, at any depth and in
  document order, or those of one type.
- `mf/property` gives the first value of a property as text, whether
  it's a string or an embedded microformat, and `mf/value` gives the
  text of one value.
- `mf/urls` gives the URLs of a property, with the url of an embedded
  h-cite, made normal, so that you can compare them.

```clojure
(def entry (first (mf/items result "h-entry")))

(mf/property entry :author)
;; => "Jane"
```

The [representative h-card](https://microformats.org/wiki/representative-h-card-parsing)
of a page and the [authorship](https://indieweb.org/authorship-spec) of
an h-entry have specs of their own, which `mf/representative-card` and
`mf/author` follow:

```clojure
(mf/author result "https://jane.example/posts/hello" entry)
;; => {:card {:type       ["h-card"]
;;            :properties {:name ["Jane"] :url ["https://jane.example/"]}
;;            :value      "Jane"}}
```

An entry can also name its author by the URL of a page alone, and then
`mf/author` gives that URL as the `:page`. The library doesn't fetch
anything, so fetch the page and parse it yourself, and `mf/author-card`
finds the author's h-card there, or else on the entry's page.

For the rels of a page alone, e.g. its rel=me links, use `mf/rels`,
which gives the `:rels` and `:rel-urls` of `mf/parse` without parsing
the microformats.

Principles
----------

- **Standards first.** The parser follows the microformats2 parsing
  specification, and the URL Standard for URLs. The code cites the
  sections that it implements.
- **The suite decides.** Where the spec is unclear, the parser does what
  the test suite expects. Where the suite contradicts itself, predates
  the spec, or depends on how a browser builds the tree of a whole page,
  the tests skip that part and say why.
- **One walk.** The class and rel microformats are parsed in one walk of
  the tree, as the spec allows, except on a page that uses the include
  pattern of microformats1.
- **One codebase.** The library is written in `.cljc`. Its only
  dependency is html-pieces.

Performance
-----------

See the [benchmarks](doc/benchmarks.md) for how much the parser adds to
the time that html-pieces takes to read a page, on the JVM and in Node.

Development
-----------

```bash
clojure -X:test                                        # the tests on the JVM
npm install                                            # once, for the Node tests
clojure -M:cljs compile test                           # the tests in Node
clojure -X:bench                                       # the benchmarks on the JVM
clojure -M:cljs release bench && node target/bench.js  # the benchmarks in Node
```

The tests run the suites of
[microformats/tests](https://github.com/microformats/tests) when they're
in `dev-resources/microformats-tests/`, which Git ignores. CI clones them
into that folder. To do the same yourself, at the commit that the parser
passes:

```bash
git clone https://github.com/microformats/tests dev-resources/microformats-tests
git -C dev-resources/microformats-tests checkout d49f5d76d0395676274c9ff15f1a14091498d7d4
```

The first time the benchmarks run on the JVM, they fetch their pages into
`bench/fixtures/`, which Git ignores. Those in Node read the pages from
there.

License
-------

The microformats-clj project is licensed under the [MIT licence](LICENSE).
