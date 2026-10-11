(ns ^:no-doc dk.simongray.microformats.html
  "Hiccup read as the DOM reads HTML: the parts of an element and its text
  content, and the ASCII whitespace and letters that HTML compares.

  The Hiccup is that of html-pieces: an element is a vector of a keyword
  tag, an optional map of attributes and the children, and text is a
  string."
  (:require [clojure.string :as str]
            [dk.simongray.html-pieces :as html-pieces])
  #?(:cljs (:import [goog.string StringBuffer])))

(defn element?
  "Whether `x` is a Hiccup element: a vector with a keyword tag."
  [x]
  (and (vector? x) (keyword? (nth x 0 nil))))

(defn tag
  "The tag of the element `node`."
  [node]
  (nth node 0))

(defn attrs
  "The attributes of the element `node`."
  [node]
  (let [x (nth node 1 nil)]
    (if (map? x) x {})))

(defn children
  "The children of the element `node`."
  [node]
  (subvec node (if (map? (nth node 1 nil)) 2 1)))

;; reduce takes a subvec by an iterator, which is slower than nth
(defn reduce-children
  "The children of the element `node` reduced by `f` from `init`, as
  reduce does."
  [f init node]
  (let [n (count node)]
    (loop [i   (if (map? (nth node 1 nil)) 2 1)
           acc init]
      (if (< i n)
        (let [acc (f acc (nth node i))]
          (if (reduced? acc)
            @acc
            (recur (inc i) acc)))
        acc))))

(defn element-children
  "The children of the element `node` that are elements."
  [node]
  (filterv element? (children node)))

;; The code of a character and the tests of it take and give primitives on
;; the JVM, so that the loops over text don't box each character.

(defn code-at
  "The code of the character at `i` of the text `s`."
  ^long [s ^long i]
  #?(:clj  (long (int (.charAt ^String s (int i))))
     :cljs (.charCodeAt s i)))

;; Infra Standard, "ASCII whitespace", which is all that HTML strips
(defn- whitespace-at?
  [s ^long i]
  (let [c (code-at s i)]
    (or (== c 32) (== c 10) (== c 9) (== c 13) (== c 12))))

(defn strip-start
  "The text `s` without the ASCII whitespace at its start."
  [^String s]
  (let [n (count s)
        i (loop [i 0]
            (if (and (< i n) (whitespace-at? s i)) (recur (inc i)) i))]
    (cond-> s (pos? i) (.substring i))))

(defn strip-end
  "The text `s` without the ASCII whitespace at its end."
  [^String s]
  (let [n (count s)
        i (loop [i n]
            (if (and (pos? i) (whitespace-at? s (dec i))) (recur (dec i)) i))]
    (cond-> s (< i n) (.substring 0 i))))

(defn strip
  "The text `s` without the ASCII whitespace at its ends."
  [s]
  (strip-start (strip-end s)))

;; Infra Standard, "ASCII lowercase", as HTML compares e.g. rel values
(defn ascii-lower-case
  "The text `s` with the ASCII letters A to Z in lower case."
  [s]
  (str/replace s #"[A-Z]+" #?(:clj  #(.toLowerCase ^String % java.util.Locale/ROOT)
                              :cljs #(.toLowerCase %))))

;; JavaScript splits a string fastest itself
(defn split-whitespace
  "The words of the text `s` that ASCII whitespace separates."
  [^String s]
  #?(:clj  (let [n (count s)]
             (loop [i 0 start 0 words (transient [])]
               (cond
                 (== i n)
                 (persistent! (cond-> words (< start i) (conj! (.substring s start i))))

                 (whitespace-at? s i)
                 (recur (inc i) (inc i) (cond-> words (< start i) (conj! (.substring s start i))))

                 :else
                 (recur (inc i) start words))))
     :cljs (vec (.filter (.split s #"[\t\n\f\r ]+") #(pos? (.-length %))))))

(defn- builder
  []
  #?(:clj  (StringBuilder.)
     :cljs (StringBuffer.)))

(defn- append!
  [b s]
  #?(:clj  (.append ^StringBuilder b ^String s)
     :cljs (.append b s)))

(def ignored-tags
  "The elements whose content is no text: script and style, and template,
  whose content is outside the document."
  #{:script :style :template})

(def block-tags
  "The elements that start and end a line of text, which html-pieces sets
  off in its text."
  (into html-pieces/paragraph-tags html-pieces/line-tags))

;; the textContent of the DOM runs words together where the HTML has no
;; whitespace between blocks, e.g. <p>One.</p><p>Two</p>
(defn text
  "The text content of the Hiccup `nodes`, without the content of the
  ignored-tags, and with each img as the text that `img-text` gives of its
  attributes, if any. A br is a line break, and so are the start and the
  end of an element of block-tags, where there's no whitespace on either
  side of them."
  [nodes img-text]
  (let [b        (builder)
        space?   (volatile! true)
        pending? (volatile! false)]
    (letfn [(add! [s]
              (when (seq s)
                (when (and @pending? (not (whitespace-at? s 0)))
                  (append! b "\n"))
                (vreset! pending? false)
                (append! b s)
                (vreset! space? (whitespace-at? s (dec (count s))))))
            ;; the break waits for the next text, which may start with
            ;; whitespace of its own
            (break! []
              (when-not @space?
                (vreset! pending? true)))
            (node [_ x]
              (cond
                (string? x)  (add! x)
                (element? x) (let [tag (tag x)]
                               (cond
                                 (ignored-tags tag) nil
                                 (= :img tag)       (some-> (img-text (attrs x)) add!)
                                 (= :br tag)        (add! "\n")
                                 (block-tags tag)   (do (break!)
                                                        (reduce-children node nil x)
                                                        (break!))
                                 :else              (reduce-children node nil x)))))]
      (reduce node nil nodes)
      (str b))))

;; https://microformats.org/wiki/textcontent-parsing, a draft that php-mf2
;; and mf2py follow, but which the microformats/tests suite doesn't
(defn collapsed-text
  "The text content of the Hiccup `nodes` with its whitespace collapsed,
  without the content of the ignored-tags, and with each img as the text
  that `img-text` gives of its attributes, if any, between spaces. A br is
  a line break, and so is the start of a p."
  [nodes img-text]
  (let [b (builder)]
    (letfn [(node [_ x]
              (cond
                (string? x)  (append! b (str/replace x #"[\t\n\r]" " "))
                (element? x) (let [tag (tag x)]
                               (cond
                                 (ignored-tags tag) nil
                                 (= :img tag)       (some->> (img-text (attrs x))
                                                             strip
                                                             (#(str " " % " "))
                                                             (append! b))
                                 (= :br tag)        (append! b "\n")
                                 (= :p tag)         (do (append! b "\n")
                                                        (reduce-children node nil x))
                                 :else              (reduce-children node nil x)))))]
      (reduce node nil nodes)
      (str/replace (str b)
                   #"^[\t\n\f\r ]+| +(?=\n)|(?<=\n) +| +(?= )|[\t\n\f\r ]+$"
                   ""))))

(defn update-attrs
  "The Hiccup `nodes` with the attributes of each element changed by `f`,
  a function of the tag and the attributes."
  [nodes f]
  (letfn [(node [x]
            (if (element? x)
              (into [(tag x) (f (tag x) (attrs x))] (map node) (children x))
              x))]
    (map node nodes)))

(defn trimmed
  "The Hiccup `nodes` without the ASCII whitespace at their ends, which is
  in the text of the first node and of the last, if they're text."
  [nodes]
  (let [v (vec nodes)
        v (cond-> v (string? (first v)) (update 0 strip-start))
        v (cond-> v (string? (peek v)) (update (dec (count v)) strip-end))]
    (cond->> v
      (or (= "" (first v)) (= "" (peek v))) (filterv #(not= "" %)))))
