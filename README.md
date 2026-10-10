microformats-clj
================

[![Clojars Project](https://img.shields.io/clojars/v/dk.simongray/microformats-clj.svg)](https://clojars.org/dk.simongray/microformats-clj)

This is a [microformats2](https://microformats.org/wiki/microformats2)
parser for Clojure and ClojureScript. It reads the h-entries, h-cards and
other microformats of a page, and its rel links, by the
[microformats2 parsing specification](https://microformats.org/wiki/microformats2-parsing).

- **To the spec.** It passes the microformats2 suites of the
  [microformats test suite](https://github.com/microformats/tests), with
  the value-class-pattern, implied properties, nested microformats and
  rels.
- **One tree.** It reads HTML with
  [html-pieces](https://github.com/simongray/html-pieces), or takes the
  Hiccup that html-pieces has already read, so you can parse a page once
  and use the tree for other things too.
- **Plain data.** The result has the shape of the spec's JSON, with
  keywords for keys.

The same code runs on the JVM, in Node and in the browser. It doesn't read
the classes of microformats1, e.g. `vcard` or `hentry`, yet.

> Like html-pieces, it was developed with assistance from frontier LLMs.

Getting started
---------------

It requires Clojure 1.11+ and Java 11+. Add it from
[Clojars](https://clojars.org/dk.simongray/microformats-clj) to the
`:deps` in your `deps.edn`:

```clojure
dk.simongray/microformats-clj {:mvn/version "0.1.0"}
```

Then parse a page with its URL, which relative URLs are resolved against:

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

The keys are keywords, but for the keys of `:rels` and `:rel-urls`, which
are strings. A property name is always made of lower-case letters, digits
and hyphens, so it makes a plain keyword. A rel value or a URL can hold
any character, and a slash makes a keyword with a namespace, e.g. the
`name` of the keyword of `http://a/b` is `/a/b`, which is also what
data.json writes as its key.

An e- property has its content as `:hiccup`, next to the `:html` and the
`:value` of the spec. It goes straight into `html/hiccup` or
`html/sanitize`, to show the content without reading its HTML again.

URLs are resolved against the page URL and the first `<base>`, and
normalized as a browser normalizes them, by the
[URL Standard](https://url.spec.whatwg.org/). So an empty `href`, e.g. of
a Webmention endpoint, is the page itself, or its `<base>` when it has
one. When the URL of the page is nil and there's no `<base>`, relative
URLs stay as they are.

The [authorship](https://indieweb.org/authorship-spec) of an h-entry and
the [representative h-card](https://microformats.org/wiki/representative-h-card-parsing)
of a page have specs of their own, which this library leaves out.

Principles
----------

- **Standards first.** The parser follows the microformats2 parsing
  specification, and URLs the URL Standard. The code cites the sections
  that it implements.
- **The suite decides.** Where the spec is unclear, the parser does what
  the test suite expects. Where the suite contradicts itself, or depends
  on how a browser builds the tree of a whole page, the tests skip that
  part and say why.
- **One walk.** The class and rel microformats are parsed in one walk of
  the tree, as the spec allows.
- **One codebase.** The library is written in `.cljc`. Its only
  dependency is html-pieces.

Performance
-----------

See the [benchmarks](doc/benchmarks.md) for the time that the parser adds
to the time that html-pieces takes to read a page, on the JVM and in Node.

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
into that folder. To do the same yourself, at the version that the parser
passes:

```bash
git clone https://github.com/microformats/tests dev-resources/microformats-tests
git -C dev-resources/microformats-tests checkout d49f5d76d0395676274c9ff15f1a14091498d7d4
```

The benchmarks on the JVM fetch their pages into `bench/fixtures/`, which
Git ignores, the first time they run, and those in Node read them from
there.

License
-------

The microformats-clj project is licensed under the [MIT licence](LICENSE).
