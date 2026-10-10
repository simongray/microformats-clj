(ns ^:no-doc dk.simongray.microformats.html
  "Hiccup read as the DOM reads HTML: the parts of an element, its text
  content, and its content as HTML, serialized by section 13.3 of the HTML
  Standard.

  The Hiccup is that of html-pieces: an element is a vector of a keyword
  tag, an optional map of attributes and the children, and text is a
  string."
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

(defn text
  "The text content of the Hiccup `nodes`, without the content of the
  ignored-tags, and with each img as the text that `img-text` gives of its
  attributes, if any."
  [nodes img-text]
  (let [b (builder)]
    (letfn [(node [_ x]
              (cond
                (string? x)  (append! b x)
                (element? x) (let [tag (tag x)]
                               (cond
                                 (ignored-tags tag) nil
                                 (= :img tag)       (some->> (img-text (attrs x))
                                                             (append! b))
                                 :else              (reduce-children node nil x)))))]
      (reduce node nil nodes)
      (str b))))

;; HTML Standard, 13.3, "Serializing HTML fragments", as of 2026-10-10

(def void-elements
  "The elements that are serialized without content or an end tag."
  #{:area :base :basefont :bgsound :br :col :embed :frame :hr :img :input
    :keygen :link :meta :param :source :track :wbr})

(def raw-text-elements
  "The elements whose text is serialized without escapes."
  #{:style :script :xmp :iframe :noembed :noframes :plaintext})

(defn- escape
  "The escape of the character of the code `c`, in an attribute value when
  `attr?`, or nil when it needs none."
  [^long c attr?]
  (case c
    38  "&amp;"
    160 "&nbsp;"
    60  "&lt;"
    62  "&gt;"
    34  (when attr? "&quot;")
    nil))

(defn- escaped!
  "Append the text `s` to the builder `b`, escaped, as an attribute value
  when `attr?`."
  [b ^String s attr?]
  (let [n (count s)]
    (loop [i 0 start 0]
      (if (== i n)
        (append! b (.substring s start n))
        (if-let [e (escape (code-at s i) attr?)]
          (do (append! b (.substring s start i))
              (append! b e)
              (recur (inc i) (inc i)))
          (recur (inc i) start))))))

(defn- attribute!
  "Append the attribute named `k` of the value `v` to the builder `b`: the
  name alone when `v` is true, and nothing when it's nil or false."
  [b k v]
  (when (and (some? v) (not (false? v)))
    (append! b " ")
    (append! b (name k))
    (when-not (true? v)
      (append! b "=\"")
      (escaped! b (str v) true)
      (append! b "\""))))

(defn html
  "The Hiccup `nodes` serialized as HTML, as the innerHTML of an element
  whose children they are."
  [nodes]
  (let [b (builder)]
    (letfn [(node [x raw?]
              (cond
                (string? x)  (if raw? (append! b x) (escaped! b x false))
                (element? x) (element x)))
            (element [x]
              (let [tag (tag x)
                    n   (name tag)]
                (append! b "<")
                (append! b n)
                (run! (fn [[k v]] (attribute! b k v)) (attrs x))
                (append! b ">")
                (when-not (void-elements tag)
                  (let [raw? (contains? raw-text-elements tag)]
                    (reduce-children #(node %2 raw?) nil x))
                  (append! b "</")
                  (append! b n)
                  (append! b ">"))))]
      (run! #(node % false) nodes)
      (str b))))

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
