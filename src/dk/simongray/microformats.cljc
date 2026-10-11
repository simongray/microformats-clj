(ns dk.simongray.microformats
  "Microformats2 parsed from HTML, or from the Hiccup of html-pieces, by
  the microformats2 parsing specification:
  https://microformats.org/wiki/microformats2-parsing

  The functions after parse read its result: the microformats of a page
  in document order, the values of their properties, the representative
  h-card of a page, and the author of an h-entry."
  (:require [dk.simongray.html-pieces :as html-pieces]
            [dk.simongray.html-pieces.url :as url]
            [dk.simongray.microformats.backcompat :as backcompat]
            [dk.simongray.microformats.html :as html]
            [dk.simongray.microformats.parser :as parser]))

(defn- nodes-of
  "The Hiccup nodes of `x`: the HTML string `x` parsed, an element, or
  nodes."
  [x]
  (cond
    (string? x)       (html-pieces/parse x)
    (html/element? x) [x]
    :else             x))

;; HTML Standard, 4.2.3 (2026-10-09): a base URL is never data: or
;; javascript:
(defn- base-of
  "The base URL of the page at `url` with the Hiccup `nodes`."
  [nodes url]
  (let [base (some->> (parser/base-href nodes) (url/resolve url))]
    (if (and base (not (contains? #{"data" "javascript"} (url/scheme base))))
      base
      url)))

(defn- env-of
  "The environment of a parse of the Hiccup `nodes` of the page at `url`,
  by `opts`."
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
  "The microformats of `x`, the HTML or the Hiccup of the page at `url`, as
  a map of :items, :rels and :rel-urls, in the shape of the JSON of the
  spec, by `opts`.

  The Hiccup is that of html-pieces/parse, so that a page can be parsed
  once for several uses. Relative URLs are resolved against `url` and the
  first base element, and they stay relative when `url` is nil and there
  is no base. An e- property has its content as :hiccup too, next to the
  :html and the :value of the spec. The classes of microformats1, e.g.
  vcard, are read as the spec's backcompat microformats, e.g. h-card.

  A text value is the text content of its element, as the spec has it, but
  with a line break at each br, and where a block element would otherwise
  run words together. With :collapse-whitespace? in `opts`, its whitespace
  is collapsed instead, as php-mf2 and mf2py do it, by the draft of
  https://microformats.org/wiki/textcontent-parsing

  With :proposed-backcompat? in `opts`, an hentry without a published date
  or an author takes them from the first time of the class entry-date and
  the first rel=author hyperlink in it, as the h-entry page of the
  microformats wiki proposes for the default themes of WordPress.

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
  "The rels of `x`, the HTML or the Hiccup of the page at `url`, as a map
  of :rels and :rel-urls, by `opts`, as parse gives them, but without
  parsing the microformats of the page."
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
  "Whether the microformat `item` is of the type `t`, e.g. h-card."
  [t item]
  (boolean (some #{t} (:type item))))

(defn- inner-items
  "The microformats in the properties of the microformat `item`, and its
  children."
  [item]
  (concat (filter :type (mapcat val (:properties item)))
          (:children item)))

(defn- in-order
  "The microformats `xs` in document order, each once, since one that's
  the value of two properties is there twice, at the same place."
  [xs]
  (->> (sort-by parser/position xs)
       (partition-by parser/position)
       (mapcat #(if (parser/position (first %)) [(first %)] %))))

(defn items
  "Every microformat in `mf2`, the result of parse, at any depth and in
  document order: those at the top of the page, in the properties of
  others, and among their children. With `type`, e.g. h-card, only those
  of that type."
  ([mf2]
   (in-order (mapcat #(tree-seq map? inner-items %) (:items mf2))))
  ([mf2 type]
   (filter #(typed? type %) (items mf2))))

(defn value
  "The plain value of `v`, a value of a property: the text of a string, or
  else the :value of an embedded microformat, of an e- property or of an
  image with alt text."
  [v]
  (if (map? v)
    (:value v)
    v))

(defn property
  "The first plain value of the property `k` of the microformat `item`
  that isn't empty, as value gives it, or nil."
  [item k]
  (some #(not-empty (value %)) (get-in item [:properties k])))

;; URL Standard, as the representative h-card page compares URLs
(defn urls
  "The URLs that the property `k` of the microformat `item` names, made
  normal by the URL Standard: each value that's an absolute URL, and the
  value and the urls of an embedded microformat, e.g. of an h-cite."
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
  "The h-cards of `mf2`, the result of parse, for the page at `url`, that
  steps 1 to 3 of representative h-card parsing give, nil where a step
  finds none."
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
  "The representative h-card of the page at `url` whose microformats are
  `mf2`, the result of parse, or nil: the first h-card whose url and uid
  are the URL of the page, or else the first whose url is that of a rel=me
  link of the page, or else its only h-card, if its url is the page's."
  [mf2 url]
  (some identity (representatives mf2 url)))

(defn- web-url
  "The URL `s`, made normal, when it's an absolute http or https URL."
  [s]
  (let [u (some->> s (url/resolve nil))]
    (when (contains? #{"http" "https"} (url/scheme u))
      u)))

;; https://indieweb.org/authorship-spec (2023-01-06), step 5
(defn- named-author
  "The author that `v`, a value of an author property, names: the :card of
  an h-card, or the URL of the author's :page, or else a :card of the text
  as the name."
  [v]
  (let [s    (value v)
        page (web-url s)]
    (cond
      (and (map? v) (typed? "h-card" v)) {:card v}
      page                               {:page page}
      (seq s)                            {:card {:type       ["h-card"]
                                                 :properties {:name [s]}}})))

;; https://indieweb.org/authorship-spec (2023-01-06), steps 1 to 6. The page
;; is a permalink page of the entry when the entry's url is the page's.
(defn author
  "The author of the h-entry `entry` of the page at `url` whose
  microformats are `mf2`, the result of parse, by the authorship
  algorithm, or nil when the page names none.

  The author is a map of either its :card, an h-card, or the URL of the
  author's :page, which author-card finds the h-card on once you've
  fetched it. An author named by text alone is a card with the name."
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

;; https://indieweb.org/authorship-spec (2023-01-06), step 7, whose first
;; two steps are those of representative h-card parsing
(defn author-card
  "The h-card of the author whose page at `page` has the microformats
  `page-mf2`, by the authorship algorithm, where `mf2` are those of the
  page of the entry. Nil when there's none.

  It's the first h-card of the author's page whose url and uid are that
  page, or else whose url is that of a rel=me link there, or else the
  first h-card of the entry's page whose url is the author's page. The
  `page-mf2` can be nil, e.g. when the author's page couldn't be fetched."
  [mf2 page page-mf2]
  (let [p (some->> page (url/resolve nil))]
    (or (some identity (take 2 (representatives page-mf2 page)))
        (first (filter #(some #{p} (urls % :url)) (items mf2 "h-card"))))))

(comment
  (parse "<a class=\"h-card\" href=\"/jane\">Jane Doe</a>" "https://example.com/")
  (rels "<a rel=me href=https://m.example/@jane>" "https://jane.example/")
  #_.)
