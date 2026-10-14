(ns ^:no-doc dk.simongray.microformats.backcompat
  "The microformats1 classes that the microformats2 parsing spec maps to
  backcompat roots and properties, and the microformats1 include pattern.

  The roots and properties come from the Parser Compatibility section of
  each vocabulary on the microformats wiki, e.g.
  https://microformats.org/wiki/h-card#Parser_Compatibility, and from the
  hNews page, https://microformats.org/wiki/hnews. Where the wiki and the
  microformats/tests suite disagree, they follow the suite."
  (:require [clojure.string :as str]
            [dk.simongray.microformats.html :as html]))

(def roots
  "The microformat type for each backcompat root class name."
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
  "The backcompat property class names for each microformat type, mapped
  to the microformats2 class names they're parsed as. A root class name
  among those, e.g. the h-item of p-item h-item, is the property's type
  when its element has no root class name of its own."
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
  "The rel values that are backcompat properties for each microformat
  type, mapped to the microformats2 class names they're parsed as. The
  value of a rel=tag property is the last segment of its URL's path, and
  the value of any other is the URL."
  {"h-entry"  {"bookmark" "u-url"
               "tag"      "p-category"}
   "h-feed"   {"tag" "p-category"}
   "h-news"   {"principles"   "u-principles"
               "item-license" "u-item-license"}
   "h-recipe" {"tag" "p-category"}
   "h-review" {"bookmark" "u-url"
               "tag"      "p-category"}})

(defn root-types
  "The types of the backcompat root class names in `classes`, sorted and
  without duplicates, e.g. [\"h-card\"]."
  [classes]
  (let [types (into [] (keep roots) classes)]
    (cond-> types
      (next types) (-> distinct sort vec))))

;; two class names can map to the same property, e.g. fn and summary in a
;; microformat that's both a vcard and a vevent, and the property should
;; only appear once
(defn property-class-names
  "The microformats2 class names that `classes` are parsed as inside a
  microformat of the backcompat `types`, in order and without
  duplicates."
  [types classes]
  (into []
        (comp (mapcat (fn [s] (keep #(get-in properties [% s]) types)))
              (mapcat html/split-whitespace)
              (distinct))
        classes))

(defn rel-properties
  "The values in `rel-values` that are properties of a microformat of the
  backcompat `types`, each paired with the microformats2 class name it's
  parsed as, e.g. [\"tag\" \"p-category\"]."
  [types rel-values]
  (for [rel   (distinct rel-values)
        :let  [s (some #(get-in rels [% rel]) types)]
        :when s]
    [rel s]))

;; https://microformats.org/wiki/rel-tag
(defn tag
  "The tag in `href`, the URL of a rel=tag link: the last non-empty
  segment of its path."
  [href]
  (->> (str/split (str/replace href #"[?#].*" "") #"/")
       (remove empty?)
       (last)))

;; https://microformats.org/wiki/h-entry#Parser_Compatibility proposes these
;; rules for the default WordPress themes, which mark up neither published
;; nor author, but they haven't been accepted
(defn proposed-properties
  "The properties that the element `node` supplies to its hentry by the
  rules the microformats wiki proposes, with URLs made absolute by
  `absolute`.

  They're a map of property to value: the datetime of a time with the
  class entry-date as published, and the URL of a rel=author link as
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
  "The elements of the Hiccup `nodes` outside templates, as a map of id to
  the first element with that id."
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
  "The id of the element that replaces `node` when `node` is an include
  link or object, or nil."
  [node]
  (let [attrs (html/attrs node)]
    (when (some #{"include"} (class-names attrs))
      (some->> (case (html/tag node) :a (:href attrs) :object (:data attrs) nil)
               (str)
               (re-matches #"#(.+)")
               (second)))))

;; https://microformats.org/wiki/hcard-parsing#Include_Pattern_and_Table_Headers
;; and microdata's itemref, which the microformats/tests suite uses
(defn- added-refs
  "The ids in the itemref of the element `node`, or in its headers when
  it's a td, whose elements are added after its children."
  [node]
  (let [attrs (html/attrs node)]
    (concat (some-> (:itemref attrs) str html/split-whitespace)
            (when (= :td (html/tag node))
              (some-> (:headers attrs) str html/split-whitespace)))))

(defn- own-element?
  "Whether the element `x` belongs to the backcompat root element `node`:
  either it's the root, or it's neither a template nor an element that
  passes `microformat?`."
  [microformat? node x]
  (or (identical? node x)
      (not (or (= :template (html/tag x)) (microformat? x)))))

(defn refs
  "The ids that the element `node` of a backcompat root and its
  descendants refer to by the include pattern. The refs inside a nested
  microformat, one that passes `microformat?`, are left out, since
  they're included when that microformat is parsed."
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
  "The element `node` of a backcompat root, with the elements in `ids`
  that it and its descendants include, except those whose ids are in
  `excluded` and those inside a microformat that passes `microformat?`.

  The `ids` are a delay of a map of id to element. An include link or
  object is replaced by the element it refers to, and the elements that
  an itemref or the headers of a td refer to are added after the
  children. An element isn't included when its id belongs to an element
  around it, or when it contains `node`, since the pattern ignores an
  include of an ancestor."
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
