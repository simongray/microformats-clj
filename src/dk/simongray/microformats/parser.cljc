(ns ^:no-doc dk.simongray.microformats.parser
  "The elements of a Hiccup tree parsed for class and rel microformats, by
  the microformats2 parsing specification, which the docstrings cite by
  section: https://microformats.org/wiki/microformats2-parsing

  The class and rel microformats are parsed in one walk of the tree, which
  the spec allows. The value-class-pattern is that of
  https://microformats.org/wiki/value-class-pattern

  TODO: the microformats1 classes, which the spec reads as backcompat
  roots and properties."
  (:require [clojure.string :as str]
            [dk.simongray.html-pieces :as html-pieces]
            [dk.simongray.microformats.datetime :as datetime]
            [dk.simongray.microformats.html :as html]))

;; Class names

;; a loop rather than the regular expression (?:[0-9a-z]+-)?[a-z]+(?:-[a-z]+)*
;; of the spec, since every class name of a microformat is checked
(defn class-name?
  "Whether the text `s` is the name of a root class or a property class
  after its prefix: an optional vendor prefix of a to z and digits, then
  words of a to z, all separated by hyphens."
  [s]
  (let [n (count s)]
    (loop [i 0 start 0 digit? false first? true]
      (if (== i n)
        (and (< start n) (not digit?))
        (let [c (html/code-at s i)]
          (cond
            (== 45 c)                  (and (< start i)
                                            (recur (inc i) (inc i) false false))
            (and (<= 97 c) (<= c 122)) (recur (inc i) start digit? first?)
            (and (<= 48 c) (<= c 57))  (and first?
                                            (recur (inc i) start true first?))
            :else                      false))))))

(defn class-names
  "The class names of the element with the attributes `attrs`."
  [attrs]
  (when-let [s (:class attrs)]
    (html/split-whitespace (str s))))

;; most elements of a page have a class, and few have a microformat class,
;; which a search of the text of the attribute tells fastest
(defn microformat-class-names
  "The class names of the element with the attributes `attrs`, when one of
  them can be a root or a property class name, or else nil."
  [attrs]
  (when-let [s (:class attrs)]
    (let [s (str s)]
      (when (or (str/includes? s "h-")
                (str/includes? s "p-")
                (str/includes? s "u-")
                (str/includes? s "e-")
                (str/includes? s "dt-"))
        (html/split-whitespace s)))))

(defn- root-class?
  [s]
  (and (str/starts-with? s "h-")
       (class-name? (subs s 2))))

(defn root-types
  "The root class names among the class names `classes`, sorted and
  without duplicates, e.g. [\"h-card\"]."
  [classes]
  (let [types (filterv root-class? classes)]
    (cond-> types
      (next types) (-> distinct sort vec))))

(defn- property-class
  "The prefix and the name of the property class name `s`, or nil."
  [s]
  (when-let [prefix (cond
                      (str/starts-with? s "p-")  "p"
                      (str/starts-with? s "u-")  "u"
                      (str/starts-with? s "e-")  "e"
                      (str/starts-with? s "dt-") "dt")]
    (let [n (subs s (inc (count prefix)))]
      (when (class-name? n)
        [prefix n]))))

;; a class name given twice is a property twice, as the tentative tests of
;; microformats/tests have it, after microformats2-parsing issue 61
(defn property-classes
  "The property class names among the class names `classes`, in order, each
  as a pair of its prefix and its name, e.g. [\"p\" \"name\"]."
  [classes]
  (into [] (keep property-class) classes))

(defn root?
  "Whether the element `node` has a root class name."
  [node]
  (boolean (seq (root-types (microformat-class-names (html/attrs node))))))

(defn- microformat-element?
  "Whether the element `node` has a root or a property class name."
  [node]
  (let [classes (microformat-class-names (html/attrs node))]
    (boolean (or (seq (root-types classes))
                 (seq (property-classes classes))))))

;; URLs and images

(defn absolute
  "The URL `s` resolved by the :resolve function of `env`, or `s` as it is
  when it can't be."
  [env s]
  (or ((:resolve env) s) s))

(defn img-value
  "The URL of the img with the attributes `attrs`, resolved by `env`, and
  its alt text when it has one, by 1.5, \"parse an img element for src and
  alt\"."
  [env attrs]
  (let [src (absolute env (str (:src attrs)))]
    (if (contains? attrs :alt)
      {:value src :alt (:alt attrs)}
      src)))

(defn img-text
  "The text of the img with the attributes `attrs` in the value of a p- or
  an e- property: its alt text, or else its URL, resolved by `env`,
  between spaces."
  [env attrs]
  (cond
    (contains? attrs :alt) (:alt attrs)
    (contains? attrs :src) (str " " (absolute env (:src attrs)) " ")))

(defn text
  "The text content of the element `node`, stripped, with each img as the
  text that `img-text` gives of its attributes, if any."
  ([node]
   (text node (constantly nil)))
  ([node img-text]
   (html/strip (html/text (html/children node) img-text))))

;; Value class pattern

(defn value-elements
  "The value elements of the property element `node`: the descendants with
  the class name value or value-title, but not those inside another value
  element, a property element or a microformat."
  [node]
  (letfn [(value? [x]
            (let [s (str (:class (html/attrs x)))]
              (and (str/includes? s "value")
                   (some #{"value" "value-title"} (class-names (html/attrs x))))))
          (walk [acc node]
            (html/reduce-children (fn [acc x]
                                    (cond
                                      (not (html/element? x))     acc
                                      (= :template (html/tag x))  acc
                                      (value? x)                  (conj acc x)
                                      (microformat-element? x)    acc
                                      :else                       (walk acc x)))
                                  acc
                                  node))]
    (walk [] node)))

(defn value-text
  "The text of the value element `node`, with the elements that hold it in
  an attribute of `attributes`, a map of tag to attribute."
  [attributes node]
  (let [attrs (html/attrs node)
        k     (get attributes (html/tag node))]
    (cond
      (some #{"value-title"} (class-names attrs)) (str (:title attrs))
      (and k (contains? attrs k))                 (get attrs k)
      (#{:img :area} (html/tag node))             ""
      :else                                       (html/text (html/children node)
                                                             (constantly nil)))))

(def value-attributes
  "The attribute that holds the value of a value element, by tag."
  {:img :alt :area :alt :data :value :abbr :title})

(def datetime-value-attributes
  "The attribute that holds the value of a value element of a dt- property,
  by tag."
  (assoc value-attributes :del :datetime :ins :datetime :time :datetime))

(defn value-class-text
  "The value of the property element `node` by the value-class-pattern: its
  value elements concatenated, or nil when it has none."
  [node]
  (when-let [elements (seq (value-elements node))]
    (apply str (map #(value-text value-attributes %) elements))))

;; Properties

(defn p-value
  "The value of the p- property element `node`, by 1.3.1, with the URLs of
  its images resolved by `env`."
  [env node]
  (let [tag   (html/tag node)
        attrs (html/attrs node)]
    (or (value-class-text node)
        (when (#{:abbr :link} tag) (:title attrs))
        (when (#{:data :input} tag) (:value attrs))
        (when (#{:img :area} tag) (:alt attrs))
        (text node #(img-text env %)))))

(defn u-value
  "The value of the u- property element `node`, by 1.3.2, resolved by
  `env`."
  [env node]
  (let [tag   (html/tag node)
        attrs (html/attrs node)
        has?  #(contains? attrs %)]
    (cond
      (and (#{:a :area :link} tag) (has? :href))
      (absolute env (:href attrs))

      (and (= :img tag) (has? :src))
      (img-value env attrs)

      (and (#{:audio :video :source :iframe} tag) (has? :src))
      (absolute env (:src attrs))

      (and (= :video tag) (has? :poster))
      (absolute env (:poster attrs))

      (and (= :object tag) (has? :data))
      (absolute env (:data attrs))

      :else
      (absolute env (or (value-class-text node)
                        (when (= :abbr tag) (:title attrs))
                        (when (#{:data :input} tag) (:value attrs))
                        (text node))))))

(defn- dt-text
  "The text of the dt- property element `node` without the
  value-class-pattern."
  [node]
  (let [tag   (html/tag node)
        attrs (html/attrs node)]
    (cond
      (and (#{:time :ins :del} tag) (contains? attrs :datetime)) (:datetime attrs)
      (and (= :abbr tag) (contains? attrs :title))               (:title attrs)
      (and (#{:data :input} tag) (contains? attrs :value))       (:value attrs)
      :else                                                      (text node))))

(defn dt-value
  "The value of the dt- property element `node`, by 1.3.3: a text, or a map
  of a :time and a :zone when it's a time without a date, which takes the
  date of another dt- property."
  [node]
  (if-let [parts (some->> (seq (value-elements node))
                          (map #(value-text datetime-value-attributes %))
                          (datetime/combined))]
    (cond-> parts (:date parts) datetime/text)
    (let [s (dt-text node)]
      (or (datetime/time-only (html/strip s)) s))))

;; microformats2-parsing issue 38, which the tests of microformats/tests
;; follow
(defn absolute-urls
  "The attributes `attrs` with their URLs resolved by `env`, but those that
  are a fragment alone, e.g. #top."
  [env attrs]
  (reduce-kv (fn [m k v]
               (if (and (html-pieces/url-attributes k)
                        (string? v)
                        (not (str/starts-with? v "#")))
                 (assoc m k (absolute env v))
                 m))
             attrs
             attrs))

(defn e-value
  "The value of the e- property element `node`, by 1.3.4: a map of its
  :html and its text :value, and its content as :hiccup, with the URLs in
  each resolved by `env`."
  [env node]
  (let [nodes (-> (html/children node)
                  (html/update-attrs (fn [_ attrs] (absolute-urls env attrs)))
                  (html/trimmed))]
    {:html   (html/strip (html/html nodes))
     :value  (html/strip (html/text nodes #(img-text env %)))
     :hiccup (apply list nodes)}))

(defn property-value
  "The value of the property element `node` of the `prefix`, with its URLs
  resolved by `env`."
  [env prefix node]
  (case prefix
    "p"  (p-value env node)
    "u"  (u-value env node)
    "dt" (dt-value node)
    "e"  (e-value env node)))

;; Implied properties, 1.3.5

(defn- only-child
  "The only element child of the element `node`, when it has one and it's
  no microformat."
  [node]
  (let [[x more] (html/element-children node)]
    (when (and x (not more) (not (root? x)))
      x)))

(defn- only-of-type
  "The only element child of the `tag` of the element `node`, when it has
  one and it's no microformat."
  [node tag]
  (let [[x more] (filter #(= tag (html/tag %)) (html/element-children node))]
    (when (and x (not more) (not (root? x)))
      x)))

(defn- non-empty-name
  "The alt of an img or an area, or the title of an abbr, the element
  `node`, when it isn't empty."
  [node]
  (let [k (case (html/tag node)
            (:img :area) :alt
            :abbr        :title
            nil)]
    (not-empty (get (html/attrs node) k))))

(defn implied-name
  "The name implied by the microformat element `node`."
  [node]
  (let [tag   (html/tag node)
        attrs (html/attrs node)]
    (html/strip (or (when (#{:img :area} tag) (:alt attrs))
                    (when (= :abbr tag) (:title attrs))
                    (some-> (only-child node) non-empty-name)
                    (some-> (only-child node) only-child non-empty-name)
                    (text node :alt)))))

(defn- photo-of
  "The photo of the img or object element `node`, resolved by `env`, or
  nil."
  [env node]
  (let [attrs (html/attrs node)]
    (case (html/tag node)
      :img    (when (contains? attrs :src) (img-value env attrs))
      :object (some->> (:data attrs) (absolute env))
      nil)))

(defn implied-photo
  "The photo implied by the microformat element `node`, resolved by `env`,
  or nil."
  [env node]
  (letfn [(photo [parent]
            (or (some->> (only-of-type parent :img) (photo-of env))
                (some->> (only-of-type parent :object) (photo-of env))))]
    (or (photo-of env node)
        (photo node)
        (some-> (only-child node) photo))))

(defn- url-of
  "The URL of the a or area element `node`, resolved by `env`, or nil."
  [env node]
  (when (#{:a :area} (html/tag node))
    (some->> (:href (html/attrs node)) (absolute env))))

(defn implied-url
  "The URL implied by the microformat element `node`, resolved by `env`, or
  nil."
  [env node]
  (letfn [(link [parent]
            (or (some->> (only-of-type parent :a) (url-of env))
                (some->> (only-of-type parent :area) (url-of env))))]
    (or (url-of env node)
        (link node)
        (some-> (only-child node) link))))

(defn with-implied
  "The `properties` of the microformat element `node` with the name, photo
  and url that it implies, resolved by `env`, by the `prefixes` of its
  properties and whether it has `nested?` microformats."
  [env node properties prefixes nested?]
  (let [imply? (fn [k blocking]
                 (not (or (contains? properties k)
                          (some prefixes blocking)
                          nested?)))]
    (cond-> properties
      (imply? :name ["p" "e"])
      (assoc :name [(implied-name node)])

      (imply? :photo ["u"])
      (as-> props (if-let [photo (implied-photo env node)]
                    (assoc props :photo [photo])
                    props))

      (imply? :url ["u"])
      (as-> props (if-let [u (implied-url env node)]
                    (assoc props :url [u])
                    props)))))

;; The value-class-pattern, "microformats2 parsers implied date"
(defn with-dates
  "The `properties` with each time that lacks a date given the date of the
  dt- property before it, or else after it. The `dts` are the paths to the
  dt- values, in document order."
  [properties dts]
  (if-not (some #(map? (get-in properties %)) dts)
    properties
    (let [dates (mapv #(datetime/date-of (get-in properties %)) dts)]
      (reduce (fn [props [i path]]
                (let [v (get-in props path)]
                  (if (map? v)
                    (let [date (or (last (keep identity (subvec dates 0 i)))
                                   (first (keep identity (subvec dates i))))]
                      (assoc-in props path (datetime/text (assoc v :date date))))
                    props)))
              properties
              (map-indexed vector dts)))))

;; Parsing a document, 1.1 and 1.2

(defn- new-frame
  "The state of the parse of a microformat whose properties are found, with
  the first value of a p-name as :p-name and of a u-url as :u-url."
  []
  {:properties {}
   :prefixes   #{}
   :dts        []
   :children   []
   :nested?    false})

(defn- add-value
  "The microformat `frame` with the value `v` added to its property `k` of
  the `prefix`."
  [frame prefix k v]
  (let [field (cond
                (and (= :name k) (= "p" prefix)) :p-name
                (and (= :url k) (= "u" prefix))  :u-url)]
    (cond-> (-> frame
                (update :properties update k (fnil conj []) v)
                (update :prefixes conj prefix))
      (and field (not (contains? frame field))) (assoc field v))))

(defn- add-properties
  "The microformat `frame` with the properties of the classes `props` of
  the element `node`, resolved by `env`, and the path to each dt- value."
  [env frame node props]
  (reduce (fn [frame [prefix n]]
            (let [k    (keyword n)
                  path [k (count (get-in frame [:properties k]))]]
              (cond-> (add-value frame prefix k (property-value env prefix node))
                (= "dt" prefix) (update :dts conj path))))
          frame
          props))

(defn item
  "The microformat of the element `node`, of the root `types`, whose
  properties and children are in `frame`, with the properties it implies
  resolved by `env`."
  [env node types frame]
  (let [{:keys [properties prefixes dts children nested?]} frame
        id (not-empty (:id (html/attrs node)))]
    (cond-> {:type       types
             :properties (-> properties
                             (with-dates dts)
                             (as-> props (with-implied env node props prefixes nested?)))}
      id             (assoc :id id)
      (seq children) (assoc :children children))))

(defn- first-value
  "The first value of the property `k` of the `item` that the `frame` of
  its parse holds as `field`, e.g. :p-name, or else the value that the
  item implies."
  [frame item field k]
  (if (contains? (:properties frame) k)
    (get frame field)
    (first (get-in item [:properties k]))))

(defn- nested-value
  "The value of the microformat `item` of the element `node` as a property
  of the `prefix`, by 1.2 and the `frame` of its parse, resolved by `env`."
  [env prefix node item frame]
  (case prefix
    "p"  {:value (or (first-value frame item :p-name :name)
                     (p-value env node))}
    "e"  (e-value env node)
    "u"  {:value (or (first-value frame item :u-url :url)
                     (u-value env node))}
    "dt" (let [v (dt-value node)]
           {:value (if (map? v) (datetime/text v) v)})))

(defn- add-item
  "The `frame` of the parent microformat, or of the document when it's
  :top?, with the microformat `item` of the element `node` added: as the
  value of its properties `props`, resolved by `env`, or else as a child.
  The `child` is the frame of the parse of the item."
  [env frame node props item child]
  (cond
    (:top? frame)
    (update frame :children conj item)

    (seq props)
    (assoc (reduce (fn [frame [prefix n]]
                     (add-value frame prefix (keyword n)
                                (merge item (nested-value env prefix node item child))))
                   frame
                   props)
           :nested? true)

    :else
    (-> frame
        (update :children conj item)
        (assoc :nested? true))))

;; Rels, 1.4

(defn- rel-link?
  "Whether an element of the `tag` can have rels."
  [tag]
  (contains? #{:a :area :link} tag))

(defn add-rel
  "The `state` of the parse with the rels of the hyperlink element `node`,
  resolved by `env`."
  [env state node]
  (let [attrs (html/attrs node)
        rels  (some-> (:rel attrs) str html/split-whitespace distinct)]
    (if (or (empty? rels) (not (contains? attrs :href)))
      state
      (let [u    (absolute env (:href attrs))
            text (html/text (html/children node) (constantly nil))
            info (cond-> (select-keys attrs [:hreflang :media :title :type])
                   (seq text) (assoc :text text))]
        (-> (reduce (fn [state rel]
                      (if (contains? (:seen state) [rel u])
                        state
                        (-> state
                            (update-in [:rels rel] (fnil conj []) u)
                            (update :seen conj [rel u]))))
                    state
                    rels)
            (update-in [:rel-urls u]
                       (fn [m]
                         (-> (merge info m)
                             (update :rels #(vec (into (sorted-set) (concat % rels))))))))))))

(def initial-state
  "The state of the parse of a document before its first node: the frame of
  the document, whose children are the top-level microformats, its rels,
  and the pairs of rel and URL that are among the rels."
  {:frame    {:top? true :children []}
   :rels     {}
   :rel-urls {}
   :seen     #{}})

(declare walk)

(defn- walk-classes
  "The `state` of the parse by `env` after the element `node` of the class
  names `classes`, which can be a microformat or a property."
  [env state node classes]
  (let [types (root-types classes)
        frame (:frame state)
        props (when-not (:top? frame) (property-classes classes))]
    (if (seq types)
      (let [state (html/reduce-children #(walk env %1 %2)
                                        (assoc state :frame (new-frame))
                                        node)
            child (:frame state)
            item  (item env node types child)]
        (assoc state :frame (add-item env frame node props item child)))
      (html/reduce-children #(walk env %1 %2)
                            (cond-> state
                              (seq props) (update :frame #(add-properties env % node props)))
                            node))))

(defn walk
  "The `state` of the parse after the Hiccup `node`, which isn't in a
  template, by the :resolve function of URLs in `env`."
  [env state node]
  (if-not (html/element? node)
    state
    (let [tag (html/tag node)]
      (if (= :template tag)
        state
        (let [state (if (rel-link? tag) (add-rel env state node) state)]
          (if-let [classes (microformat-class-names (html/attrs node))]
            (walk-classes env state node classes)
            (html/reduce-children #(walk env %1 %2) state node)))))))

(defn base-href
  "The href of the first base element of the Hiccup `nodes` that has one,
  outside a template."
  [nodes]
  (letfn [(href [_ x]
            (when (and (html/element? x) (not= :template (html/tag x)))
              (if (and (= :base (html/tag x)) (contains? (html/attrs x) :href))
                (reduced (:href (html/attrs x)))
                (some-> (html/reduce-children href nil x) reduced))))]
    (reduce href nil nodes)))
