(ns dk.simongray.microformats
  "Microformats2 parsed from HTML, or from the Hiccup of html-pieces, by
  the microformats2 parsing specification:
  https://microformats.org/wiki/microformats2-parsing"
  (:require [dk.simongray.html-pieces :as html-pieces]
            [dk.simongray.html-pieces.url :as url]
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

(defn parse
  "The microformats of `x`, the HTML or the Hiccup of the page at `url`, as
  a map of :items, :rels and :rel-urls, in the shape of the JSON of the
  spec.

  The Hiccup is that of html-pieces/parse, so that a page can be parsed
  once for several uses. Relative URLs are resolved against `url` and the
  first base element, and they stay relative when `url` is nil and there
  is no base. An e- property has its content as :hiccup too, next to the
  :html and the :value of the spec.

      (parse \"<a class=h-card href=/jane>Jane</a>\" \"https://example.com/\")
      ;; => {:items    [{:type       [\"h-card\"]
      ;;                 :properties {:name [\"Jane\"]
      ;;                              :url  [\"https://example.com/jane\"]}}]
      ;;     :rels     {}
      ;;     :rel-urls {}}"
  [x url]
  (let [nodes    (nodes-of x)
        base     (if-let [href (parser/base-href nodes)]
                   (or (url/resolve url href) url)
                   url)
        resolver (url/resolver base)
        ;; a URL that isn't valid stays as it's written
        absolute #(or (resolver %) %)
        state    (reduce #(parser/walk absolute %1 %2) parser/initial-state nodes)]
    {:items    (get-in state [:frame :children])
     :rels     (:rels state)
     :rel-urls (:rel-urls state)}))

(comment
  (parse "<a class=\"h-card\" href=\"/jane\">Jane Doe</a>" "https://example.com/")
  #_.)
