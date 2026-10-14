(ns dk.simongray.microformats
  "A microformats2 parser for HTML, and for the Hiccup that html-pieces
  returns, following the parsing specification:
  https://microformats.org/wiki/microformats2-parsing

  The other functions take the result of parse and return the
  microformats of a page in document order, the values of their
  properties, the representative h-card of a page or the author of an
  h-entry."
  (:require [dk.simongray.html-pieces :as html-pieces]
            [dk.simongray.microformats.backcompat :as backcompat]
            [dk.simongray.microformats.html :as html]
            [dk.simongray.microformats.parser :as parser]
            [dk.simongray.whatwg-url :as url]))

(defn- nodes-of
  "The Hiccup nodes of `x`, which is an HTML string to parse, an element
  or a sequence of nodes."
  [x]
  (cond
    (string? x)       (html-pieces/parse x)
    (html/element? x) [x]
    :else             x))

;; HTML Standard, 4.2.3 (2026-10-09): a base URL is never data: or
;; javascript:
(defn- base-of
  "The base URL of `nodes`, the Hiccup of the page at `url`."
  [nodes url]
  (let [base (some->> (parser/base-href nodes) (url/resolve url))]
    (if (and base (not (contains? #{"data" "javascript"} (url/scheme base))))
      base
      url)))

(defn- env-of
  "The environment for parsing `nodes`, the Hiccup of the page at `url`,
  with `opts`."
  [nodes url opts]
  (let [resolver (url/resolver (base-of nodes url))]
    ;; a URL that isn't valid stays as it's written
    {:absolute             #(or (resolver %) %)
     :text                 (if (:collapse-whitespace? opts)
                             html/collapsed-text
                             html/text)
     :ids                  (delay (backcompat/ids nodes))
     :excluded             #{}
     :proposed-backcompat? (:proposed-backcompat? opts)}))

(defn parse
  "The microformats of `x`, the HTML or Hiccup of the page at `url`,
  parsed with `opts` into a map of :items, :rels and :rel-urls in the
  spec's JSON format.

  It takes the Hiccup that html-pieces/parse returns, so that a page can
  be parsed once for several uses. Relative URLs resolve
  against the first base element, or else `url`, and stay relative when
  `url` is nil and there's no base. Besides the spec's :html and :value,
  an e- property includes its content as :hiccup. Microformats1 classes
  are read as the spec's backcompat microformats, e.g. vcard as h-card.

  A text value is its element's text content, as in the spec, but with a
  line break for each br, and wherever a block element would otherwise
  run two words together. The options are:

  - :collapse-whitespace? collapses the whitespace of text values
    instead, as php-mf2 and mf2py do, following the draft at
    https://microformats.org/wiki/textcontent-parsing
  - :proposed-backcompat? applies the two rules that the h-entry page of
    the microformats wiki proposes for WordPress themes: an hentry without
    a published date gets the datetime of its first time element with the
    class entry-date, and one without an author gets the URL of its first
    rel=author link.

      (parse \"<a class=h-card href=/jane>Jane</a>\" \"https://example.com/\")
      ;; => {:items    [{:type       [\"h-card\"]
      ;;                 :properties {:name [\"Jane\"]
      ;;                              :url  [\"https://example.com/jane\"]}}]
      ;;     :rels     {}
      ;;     :rel-urls {}}"
  ([x url]
   (parse x url {}))
  ([x url opts]
   (let [nodes (nodes-of x)
         env   (env-of nodes url opts)
         state (reduce #(parser/walk env %1 %2) parser/initial-state nodes)]
     {:items    (get-in state [:frame :children])
      :rels     (:rels state)
      :rel-urls (:rel-urls state)})))

(defn rels
  "The rels of `x`, the HTML or Hiccup of the page at `url`, as the map of
  :rels and :rel-urls that parse returns with `opts`, but without parsing
  the microformats."
  ([x url]
   (rels x url {}))
  ([x url opts]
   (let [nodes (nodes-of x)
         env   (env-of nodes url opts)
         state (reduce #(parser/walk-rels env %1 %2)
                       parser/initial-state
                       nodes)]
     (select-keys state [:rels :rel-urls]))))

(defn- typed?
  "Whether the microformat `item` has the type `t`, e.g. h-card."
  [t item]
  (boolean (some #{t} (:type item))))

(defn- inner-items
  "The microformats among the property values and the children of the
  microformat `item`."
  [item]
  (concat (filter :type (mapcat val (:properties item)))
          (:children item)))

(defn- in-order
  "The microformats `xs` in document order without duplicates, since a
  microformat that's the value of two properties appears twice at the
  same position."
  [xs]
  (->> (sort-by parser/position xs)
       (partition-by parser/position)
       (mapcat #(if (parser/position (first %)) [(first %)] %))))

(defn items
  "Every microformat in `mf2`, the result of parse, in document order, or
  only those of `type`, e.g. h-card. Nested microformats are included,
  both those in property values and those among children."
  ([mf2]
   (in-order (mapcat #(tree-seq map? inner-items %) (:items mf2))))
  ([mf2 type]
   (filter #(typed? type %) (items mf2))))

(defn value
  "The plain value of `v`, a property value: `v` itself when it's a
  string, or else the :value of an embedded microformat, an e- property or
  an image with alt text."
  [v]
  (if (map? v)
    (:value v)
    v))

(defn property
  "The first non-empty plain value of the property `k` of the microformat
  `item`, or nil."
  [item k]
  (some #(not-empty (value %)) (get-in item [:properties k])))

;; URL Standard, for comparing URLs as the representative h-card page does
(defn urls
  "The URLs in the property `k` of the microformat `item`, normalized by
  the URL Standard: each value that's an absolute URL, and the value and
  urls of an embedded microformat such as an h-cite."
  [item k]
  (distinct
   (for [v     (get-in item [:properties k])
         u     (if (map? v)
                 (cons (:value v) (get-in v [:properties :url]))
                 [v])
         :let  [normal (when (string? u) (url/resolve nil u))]
         :when normal]
     normal)))

;; https://microformats.org/wiki/representative-h-card-parsing (2021-06-22).
;; The page says "an h-card", so a nested one counts too.
(defn- representatives
  "The h-cards that steps 1 to 3 of representative h-card parsing find in
  `mf2`, the result of parsing the page at `url`, with nil for a step
  that finds none."
  [mf2 url]
  (let [page   (some->> url (url/resolve nil))
        cards  (items mf2 "h-card")
        me     (set (keep #(url/resolve nil %) (get-in mf2 [:rels "me"])))
        named? (fn [k card]
                 (some #{page} (urls card k)))]
    [(first (filter #(and (named? :url %) (named? :uid %)) cards))
     (first (filter #(some me (urls % :url)) cards))
     (when (= 1 (count cards))
       (first (filter #(named? :url %) cards)))]))

(defn representative-card
  "The representative h-card in `mf2`, the result of parsing the page at
  `url`, or nil. That's the first h-card whose url and uid are the page's
  URL, or else the first whose url is one of the page's rel=me links, or
  else the page's only h-card, if its url is the page's URL."
  [mf2 url]
  (some identity (representatives mf2 url)))

(defn- web-url
  "The URL `s`, normalized, when it's an absolute http or https URL."
  [s]
  (let [u (some->> s (url/resolve nil))]
    (when (contains? #{"http" "https"} (url/scheme u))
      u)))

;; https://indieweb.org/authorship-spec (2023-01-06), step 5
(defn- named-author
  "The author named by `v`, a value of an author property: an h-card as
  :card, or else the URL of the author's page as :page, or else a :card
  with the text as its name."
  [v]
  (let [s    (value v)
        page (web-url s)]
    (cond
      (and (map? v) (typed? "h-card" v)) {:card v}
      page                               {:page page}
      (seq s)                            {:card {:type       ["h-card"]
                                                 :properties {:name [s]}}})))

;; https://indieweb.org/authorship-spec (2023-01-06), steps 1 to 6. The page
;; is the entry's permalink page when the entry's url is the page's URL.
(defn author
  "The author of the h-entry `entry` in `mf2`, the result of parsing the
  page at `url`, by the authorship algorithm, or nil when there's none.

  The author is a map of either an h-card as :card, or the URL of the
  author's page as :page. Once you've fetched and parsed that page,
  author-card finds the h-card on it. An author named only by text is a
  :card with that name."
  [mf2 url entry]
  (let [first-author #(first (get-in % [:properties :author]))
        feed         (some #(when (some #{entry} (:children %)) %)
                           (items mf2 "h-feed"))
        permalink?   (some #{(some->> url (url/resolve nil))}
                           (urls entry :url))]
    (or (some-> (or (first-author entry) (first-author feed)) named-author)
        (when permalink?
          (some->> (first (get-in mf2 [:rels "author"]))
                   web-url
                   (hash-map :page))))))

;; https://indieweb.org/authorship-spec (2023-01-06), step 7, which starts
;; with the first two steps of representative h-card parsing
(defn author-card
  "The h-card of the author whose page is `page`, or nil, by the
  authorship algorithm, where `mf2` and `page-mf2` are the results of
  parse for the entry's page and the author's page.

  That's the first h-card on the author's page whose url and uid are the
  page's URL, or else whose url is one of its rel=me links, or else the
  first h-card on the entry's page whose url is the author's page. It
  also takes a nil `page-mf2`, e.g. when the author's page couldn't be
  fetched."
  [mf2 page page-mf2]
  (let [p (some->> page (url/resolve nil))]
    (or (some identity (take 2 (representatives page-mf2 page)))
        (first (filter #(some #{p} (urls % :url)) (items mf2 "h-card"))))))

(comment
  (parse "<a class=\"h-card\" href=\"/jane\">Jane Doe</a>" "https://example.com/")
  (rels "<a rel=me href=https://m.example/@jane>" "https://jane.example/")
  #_.)
