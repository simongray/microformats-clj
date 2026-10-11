(ns ^:no-doc dk.simongray.microformats.backcompat
  "The classes of microformats1 that the microformats2 parsing spec reads
  as backcompat roots and properties, and the include pattern of
  microformats1.

  The roots and properties are those of the Parser Compatibility section
  of each vocabulary on the microformats wiki, e.g.
  https://microformats.org/wiki/h-card#Parser_Compatibility, and those of
  hNews, https://microformats.org/wiki/hnews. Where the wiki and the
  microformats/tests suite disagree, they follow the suite."
  (:require [clojure.string :as str]
            [dk.simongray.microformats.html :as html]))

(def roots
  "The type of microformat of each backcompat root class name."
  {"adr"               "h-adr"
   "geo"               "h-geo"
   "hentry"            "h-entry"
   "hfeed"             "h-feed"
   "hnews"             "h-news"
   "hproduct"          "h-product"
   "hrecipe"           "h-recipe"
   "hresume"           "h-resume"
   "hreview"           "h-review"
   "hreview-aggregate" "h-review-aggregate"
   "vcard"             "h-card"
   "vevent"            "h-event"})

(def properties
  "The backcompat property class names of each type of microformat, each
  with the microformats2 class names that it's parsed as. A root class
  name among those is the type of the property when its element has no
  root class name of its own."
  {"h-adr"              {"post-office-box"  "p-post-office-box"
                         "extended-address" "p-extended-address"
                         "street-address"   "p-street-address"
                         "locality"         "p-locality"
                         "region"           "p-region"
                         "postal-code"      "p-postal-code"
                         "country-name"     "p-country-name"}
   "h-geo"              {"latitude"  "p-latitude"
                         "longitude" "p-longitude"}
   "h-card"             {"fn"                "p-name"
                         "honorific-prefix"  "p-honorific-prefix"
                         "given-name"        "p-given-name"
                         "additional-name"   "p-additional-name"
                         "family-name"       "p-family-name"
                         "honorific-suffix"  "p-honorific-suffix"
                         "nickname"          "p-nickname"
                         "email"             "u-email"
                         "logo"              "u-logo"
                         "photo"             "u-photo"
                         "url"               "u-url"
                         "uid"               "u-uid"
                         "category"          "p-category"
                         "adr"               "p-adr"
                         "extended-address"  "p-extended-address"
                         "street-address"    "p-street-address"
                         "locality"          "p-locality"
                         "region"            "p-region"
                         "postal-code"       "p-postal-code"
                         "country-name"      "p-country-name"
                         "label"             "p-label"
                         "geo"               "p-geo"
                         "latitude"          "p-latitude"
                         "longitude"         "p-longitude"
                         "tel"               "p-tel"
                         "note"              "p-note"
                         "bday"              "dt-bday"
                         "key"               "p-key"
                         "org"               "p-org"
                         "organization-name" "p-organization-name"
                         "organization-unit" "p-organization-unit"
                         "title"             "p-job-title"
                         "role"              "p-role"
                         "tz"                "p-tz"
                         "rev"               "dt-rev"
                         "sound"             "u-sound"
                         "agent"             "p-agent"
                         "mailer"            "p-mailer"
                         "class"             "p-class"
                         "sort-string"       "p-sort-string"}
   "h-entry"            {"entry-title"   "p-name"
                         "entry-summary" "p-summary"
                         "entry-content" "e-content"
                         "published"     "dt-published"
                         "updated"       "dt-updated"
                         "author"        "p-author"
                         "category"      "p-category"}
   "h-event"            {"summary"     "p-name"
                         "dtstart"     "dt-start"
                         "dtend"       "dt-end"
                         "duration"    "dt-duration"
                         "description" "p-description"
                         "url"         "u-url"
                         "category"    "p-category"
                         "location"    "p-location"
                         "geo"         "p-location"
                         "attendee"    "p-attendee"}
   "h-feed"             {"site-title"       "p-name"
                         "site-description" "p-summary"
                         "author"           "p-author"
                         "url"              "u-url"
                         "photo"            "u-photo"}
   "h-item"             {"fn"    "p-name"
                         "photo" "u-photo"
                         "url"   "u-url"}
   "h-news"             {"entry"      "p-entry"
                         "source-org" "p-source-org"
                         "dateline"   "p-dateline"
                         "geo"        "p-geo"}
   "h-product"          {"fn"          "p-name"
                         "photo"       "u-photo"
                         "brand"       "p-brand"
                         "category"    "p-category"
                         "description" "p-description"
                         "identifier"  "u-identifier"
                         "url"         "u-url"
                         "review"      "p-review"
                         "price"       "p-price"}
   "h-recipe"           {"fn"           "p-name"
                         "ingredient"   "p-ingredient"
                         "yield"        "p-yield"
                         "instructions" "e-instructions"
                         "duration"     "dt-duration"
                         "photo"        "u-photo"
                         "summary"      "p-summary"
                         "author"       "p-author"
                         "nutrition"    "p-nutrition"
                         "category"     "p-category"}
   "h-resume"           {"summary"     "p-summary"
                         "contact"     "p-contact"
                         "education"   "p-education"
                         "experience"  "p-experience"
                         "skill"       "p-skill"
                         "affiliation" "p-affiliation"}
   "h-review"           {"summary"     "p-name"
                         "item"        "p-item h-item"
                         "reviewer"    "p-author"
                         "dtreviewed"  "dt-published"
                         "rating"      "p-rating"
                         "best"        "p-best"
                         "worst"       "p-worst"
                         "description" "e-content"}
   "h-review-aggregate" {"summary" "p-name"
                         "item"    "p-item h-item"
                         "rating"  "p-rating"
                         "average" "p-average"
                         "best"    "p-best"
                         "worst"   "p-worst"
                         "count"   "p-count"
                         "votes"   "p-votes"}})

(def rels
  "The rel values that are backcompat properties of each type of
  microformat, each with the microformats2 class name that it's parsed
  as. The value of rel=tag is the last segment of the path of its URL, and
  that of any other the URL."
  {"h-entry"  {"bookmark" "u-url"
               "tag"      "p-category"}
   "h-feed"   {"tag" "p-category"}
   "h-news"   {"principles"   "u-principles"
               "item-license" "u-item-license"}
   "h-recipe" {"tag" "p-category"}
   "h-review" {"bookmark" "u-url"
               "tag"      "p-category"}})

(defn root-types
  "The types of the backcompat root class names among the class names
  `classes`, sorted and without duplicates, e.g. [\"h-card\"]."
  [classes]
  (let [types (into [] (keep roots) classes)]
    (cond-> types
      (next types) (-> distinct sort vec))))

;; two class names can be the same property, e.g. fn and summary inside a
;; microformat that's both a vcard and a vevent, which is that property once
(defn property-class-names
  "The microformats2 class names that the class names `classes` are parsed
  as inside a microformat of the backcompat `types`, in order and without
  duplicates."
  [types classes]
  (into []
        (comp (mapcat (fn [s] (keep #(get-in properties [% s]) types)))
              (mapcat html/split-whitespace)
              (distinct))
        classes))

(defn rel-properties
  "The rel values among `rel-values` that are properties of a microformat
  of the backcompat `types`, each with the microformats2 class name that
  it's parsed as, e.g. [\"tag\" \"p-category\"]."
  [types rel-values]
  (for [rel   (distinct rel-values)
        :let  [s (some #(get-in rels [% rel]) types)]
        :when s]
    [rel s]))

;; https://microformats.org/wiki/rel-tag
(defn tag
  "The tag of the URL `href` of a rel=tag hyperlink: the last segment of its
  path that isn't empty."
  [href]
  (->> (str/split (str/replace href #"[?#].*" "") #"/")
       (remove empty?)
       (last)))

;; https://microformats.org/wiki/h-entry#Parser_Compatibility proposes these
;; for the default themes of WordPress, which mark up neither published nor
;; author, but haven't accepted them
(defn proposed-properties
  "The properties that the element `node` gives the hentry it's in by the
  rules that the microformats wiki proposes, as a map of property to
  value: the datetime of a time of the class entry-date as published, and
  the URL of a rel=author hyperlink, made absolute by `absolute`, as
  author."
  [absolute node]
  (let [tag   (html/tag node)
        attrs (html/attrs node)
        words #(some-> % str html/split-whitespace set)]
    (cond-> {}
      (and (= :time tag)
           (contains? (words (:class attrs)) "entry-date")
           (not-empty (:datetime attrs)))
      (assoc :published (:datetime attrs))

      (and (#{:a :area :link} tag)
           (contains? attrs :href)
           (contains? (words (some-> (:rel attrs) str html/ascii-lower-case)) "author"))
      (assoc :author (absolute (:href attrs))))))

;; The include pattern: https://microformats.org/wiki/include-pattern

(defn ids
  "The elements of the Hiccup `nodes` by their ids, the first of each,
  outside templates."
  [nodes]
  (letfn [(node [m x]
            (if (and (html/element? x) (not= :template (html/tag x)))
              (let [id (:id (html/attrs x))
                    m  (cond-> m
                         (and id (not (contains? m id))) (assoc id x))]
                (html/reduce-children node m x))
              m))]
    (reduce node {} nodes)))

(defn- class-names
  [attrs]
  (some-> (:class attrs) str html/split-whitespace))

(defn- replacing-ref
  "The id that the element `node` refers to as an include hyperlink or an
  include object, which it's replaced by, or nil."
  [node]
  (let [attrs (html/attrs node)]
    (when (some #{"include"} (class-names attrs))
      (some->> (case (html/tag node) :a (:href attrs) :object (:data attrs) nil)
               (str)
               (re-matches #"#(.+)")
               (second)))))

;; https://microformats.org/wiki/hcard-parsing#Include_Pattern_and_Table_Headers
;; and the itemref of microdata, which the tests of microformats/tests use
(defn- added-refs
  "The ids that the element `node` refers to by its itemref, or by its
  headers when it's a td, whose elements are added after its children."
  [node]
  (let [attrs (html/attrs node)]
    (concat (some-> (:itemref attrs) str html/split-whitespace)
            (when (= :td (html/tag node))
              (some-> (:headers attrs) str html/split-whitespace)))))

(defn- own-element?
  "Whether the element `x` belongs to the backcompat root element `node`: it
  is the root, or it's neither a template nor a microformat by
  `microformat?`."
  [microformat? node x]
  (or (identical? node x)
      (not (or (= :template (html/tag x)) (microformat? x)))))

(defn refs
  "The ids that the element `node` of a backcompat root and its descendants
  refer to by the include pattern, but not those of a microformat by
  `microformat?` or inside one, which refer for themselves."
  [microformat? node]
  (let [own? (partial own-element? microformat? node)]
    (->> (tree-seq own? html/element-children node)
         (filter own?)
         (mapcat #(cons (replacing-ref %) (added-refs %)))
         (remove nil?)
         (set))))

(defn- contains-node?
  "Whether the element `x` is the element `node` or contains it."
  [x node]
  (boolean (some #(identical? node %) (tree-seq html/element? html/children x))))

(defn with-includes
  "The element `node` of a backcompat root with what it and its descendants
  include from `ids`, a delay of a map of id to element.

  An include hyperlink or object is replaced by the element it refers to,
  and the elements that an itemref or the headers of a td refer to are
  added after the children. An element isn't included when its id is in
  `excluded` or is that of an element around it, or when it contains
  `node`, since the pattern ignores an include of an ancestor. A
  descendant that's a microformat by `microformat?` is left as it is."
  [ids excluded microformat? node]
  (letfn [(target [seen id]
            (when-let [x (get @ids id)]
              (when-not (or (seen id) (contains-node? x node))
                x)))
          (child [seen x]
            (if-not (and (html/element? x) (own-element? microformat? node x))
              x
              (if-let [y (some->> (replacing-ref x) (target seen))]
                (child seen y)
                (element seen x))))
          (element [seen x]
            (let [seen (cond-> seen (:id (html/attrs x)) (conj (:id (html/attrs x))))]
              (into [(html/tag x) (html/attrs x)]
                    (map #(child seen %))
                    (concat (html/children x) (keep #(target seen %) (added-refs x))))))]
    (element excluded node)))
