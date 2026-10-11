(ns ^:no-doc dk.simongray.microformats.backcompat
  "The classes of microformats1 that the microformats2 parsing spec reads
  as backcompat roots and properties.

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
