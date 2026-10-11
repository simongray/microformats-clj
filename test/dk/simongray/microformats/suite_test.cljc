(ns dk.simongray.microformats.suite-test
  "The parser against the test suite of microformats/tests, on the JVM and
  in Node, when the suite is in dev-resources/microformats-tests, as the
  README tells. Without it, nothing is checked.

  Each case is the HTML of a file and the JSON that it parses to. The
  output is compared without the :hiccup of e- properties, which the JSON
  doesn't have, and with these allowances:

  - URLs are compared after they're normalized, since the suite leaves
    normalization undefined, e.g. http://example.test is
    http://example.test/
  - the unit tests of microformats-v2-unit accept a time zone with or
    without a colon, e.g. +00:00 or +0000, as their comments tell
  - an item that depends on how a browser builds the tree of a whole page,
    or that the suite contradicts itself on, is left out by its type, as
    skipped-items lists

  The unit tests run with :collapse-whitespace? too, since they're written
  to pass with or without it."
  (:require #?(:cljs ["fs" :as fs])
            #?(:clj [clojure.data.json :as json])
            #?(:clj [clojure.java.io :as io])
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [dk.simongray.html-pieces.url :as url]
            [dk.simongray.microformats :as mf]))

(def dir
  "Where the test suites of microformats/tests are."
  "dev-resources/microformats-tests/tests")

(defn- files
  "The paths of the files in the directory `path` and those below it."
  [path]
  #?(:clj  (->> (file-seq (io/file path))
                (filter #(.isFile ^java.io.File %))
                (map str))
     :cljs (if (fs/existsSync path)
             (mapcat (fn [name]
                       (let [p (str path "/" name)]
                         (if (.isDirectory (fs/statSync p))
                           (files p)
                           [p])))
                     (fs/readdirSync path))
             [])))

(defn- file-exists?
  [path]
  #?(:clj  (.exists (io/file path))
     :cljs (fs/existsSync path)))

(defn- read-file
  [path]
  #?(:clj  (slurp path)
     :cljs (fs/readFileSync path "utf8")))

(defn- read-json
  [s]
  #?(:clj  (json/read-str s)
     :cljs (js->clj (js/JSON.parse s))))

(defn cases
  "The cases of the `suite`, e.g. microformats-v2, sorted by their path
  below it, or none when it isn't there."
  [suite]
  (let [root (str dir "/" suite "/")]
    (sort-by :name
             (for [path  (files (str dir "/" suite))
                   :when (str/ends-with? path ".html")
                   :let  [json (str/replace path #"\.html$" ".json")]
                   :when (file-exists? json)]
               {:name     (subs path (count root) (- (count path) 5))
                :html     (read-file path)
                :expected (read-json (read-file json))}))))

(defn json-form
  "The parse `result` in the form of the JSON of the tests: string keys and
  no :hiccup."
  [result]
  (walk/postwalk (fn [x]
                   (if (map? x)
                     (into {} (for [[k v] x :when (not= :hiccup k)]
                                [(if (keyword? k) (name k) k) v]))
                     x))
                 result))

(defn normalized-urls
  "The `result` with each text that is an http or https URL normalized."
  [result]
  (walk/postwalk (fn [x]
                   (if (and (string? x) (re-find #"^https?://" x))
                     (or (url/resolve nil x) x)
                     x))
                 result))

(defn without-zone-colons
  "The `result` with the colon of each time zone at the end of a date and
  time taken out, e.g. 2000-01-01 00:00:00+0000."
  [result]
  (walk/postwalk (fn [x]
                   (if (string? x)
                     (str/replace x #"^(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?[+-]\d{2}):(\d{2})$" "$1$2")
                     x))
                 result))

(def skipped-items
  "The types of the top-level items that are left out of a case, by the
  case, and why."
  {"implied/implied-url"
   {:why   (str "An a inside an a, which a browser takes apart by the adoption "
                "agency algorithm, making a copy of the outer a. See "
                "doc/html-pieces-notes.md.")
    :types #{"h-test-a-onlygrandchildoftype-root-a"
             "h-test-a-onlygrandchildoftype-root-a-with-href"
             "h-test-a-onlygrandchildoftype-root-a-with-href-empty"}}

   "nested/nested-microformat-mistyped"
   {:why   (str "A u- property falls back to the text of its element without "
                "resolving it as a URL, which contradicts the spec and "
                "h-test-as-u of nested/nested-microformat. The case says that "
                "no parser does this.")
    :types #{"h-test-as-u-with-p-url"
             "h-test-as-u-with-dt-url"
             "h-test-as-u-with-e-url"}}})

(defn- without-types
  "The parse `result` in JSON form without the top-level items of `types`."
  [result types]
  (update result "items" (fn [items]
                           (vec (remove #(some types (get % "type")) items)))))

(defn comparable
  "The parse `result` of the case `name` of the `suite`, in JSON form, as
  it's compared."
  [suite name result]
  (cond-> (-> (normalized-urls result)
              (without-types (get-in skipped-items [name :types] #{})))
    (= "microformats-v2-unit" suite) without-zone-colons))

(defn actual
  "The JSON form of the parse of the HTML `s` of a case at the base URL
  `url`, by `opts`."
  ([s url]
   (actual s url {}))
  ([s url opts]
   (json-form (mf/parse s url opts))))

(defn failures
  "The cases of the `suite` that fail at the base URL `url`, but those
  `skipped`, with their expected and actual results as they're compared."
  ([suite url]
   (failures suite url #{}))
  ([suite url skipped]
   (for [{:keys [name html expected]} (cases suite)
         :when (not (skipped name))
         :let  [expected (comparable suite name expected)
                actual   (comparable suite name (actual html url))]
         :when (not= expected actual)]
     {:name name :expected expected :actual actual})))

(defn- check-suite
  ([suite url skipped]
   (check-suite suite url skipped {}))
  ([suite url skipped opts]
   (doseq [{:keys [name html expected]} (cases suite)
           :when (not (skipped name))]
     (testing name
       (is (= (comparable suite name expected)
              (comparable suite name (actual html url opts))))))))

(def mixed-microformats1
  "The cases of microformats-mixed that need the microformats1 classes."
  #{;; adr inside an h-card
    "h-card/mixedproperties"
    ;; vcard and vevent inside an h-resume
    "h-resume/mixedroots"})

(deftest microformats-v2
  (check-suite "microformats-v2" "http://example.com/" #{}))

(deftest microformats-v2-unit
  (check-suite "microformats-v2-unit" "http://example.test/" #{}))

(deftest microformats-v2-unit-with-whitespace-collapsed
  (check-suite "microformats-v2-unit" "http://example.test/" #{}
               {:collapse-whitespace? true}))

(deftest microformats-mixed
  (check-suite "microformats-mixed" "http://example.com/" mixed-microformats1))

(comment
  (map :name (failures "microformats-v2" "http://example.com/"))
  (map :name (failures "microformats-v2-unit" "http://example.test/"))
  (map :name (failures "microformats-mixed" "http://example.com/"))
  #_.)
