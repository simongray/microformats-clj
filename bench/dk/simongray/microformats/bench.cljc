(ns dk.simongray.microformats.bench
  "The time that the parser takes on a few large real pages, next to the
  time that html-pieces/parse alone takes on them, on the JVM and in Node.

      clojure -X:bench                                       ; the JVM
      clojure -M:cljs release bench && node target/bench.js  ; Node

  The JVM fetches the pages into bench/fixtures, which Git ignores, the
  first time it runs, and Node reads them from there. Each round times
  every measure once, in turn, so that a change in the load of the machine
  affects them alike, and the median of the rounds is reported."
  (:require #?(:clj [clojure.java.io :as io])
            #?(:cljs ["fs" :as fs])
            #?(:cljs [goog.string :as gstring])
            #?(:cljs [goog.string.format])
            [dk.simongray.html-pieces :as html-pieces]
            [dk.simongray.microformats :as mf]))

(def fixtures
  "The pages, each at a URL that gives the same page each time."
  [{:name "indieweb-chat-2015-07-12"
    :url  "https://chat.indieweb.org/2015-07-12"
    :what "IndieWeb chat log, an h-entry with an h-card per message"}
   {:name "indieweb-chat-2017-06-24"
    :url  "https://chat.indieweb.org/2017-06-24"
    :what "IndieWeb chat log, an h-entry with an h-card per message"}
   {:name "wikipedia-falcon-9-launches"
    :url  "https://en.wikipedia.org/w/index.php?title=List_of_Falcon_9_and_Falcon_Heavy_launches&oldid=1379499403"
    :what "Wikipedia list, thousands of links with rels"}
   {:name "ecma-262-2025"
    :url  "https://tc39.es/ecma262/2025/"
    :what "ECMAScript 2025, one page, no microformats"}])

(defn- fixture-path
  [{:keys [name]}]
  (str "bench/fixtures/" name ".html"))

#?(:clj
   (defn fetch!
     "Fetch each of the `fixtures` that isn't in bench/fixtures yet."
     [fixtures]
     (doseq [fixture fixtures
             :let    [file (io/file (fixture-path fixture))]
             :when   (not (.exists file))]
       (println "Fetching" (:url fixture))
       (io/make-parents file)
       (with-open [in (io/input-stream (:url fixture))]
         (io/copy in file)))))

(defn- read-fixture
  [fixture]
  #?(:clj  (slurp (fixture-path fixture))
     :cljs (fs/readFileSync (fixture-path fixture) "utf8")))

(defn- size
  "The size of the file of the `fixture` in bytes."
  [fixture]
  #?(:clj  (.length (io/file (fixture-path fixture)))
     :cljs (.-size (fs/statSync (fixture-path fixture)))))

(defn- now-ms
  []
  #?(:clj  (/ (System/nanoTime) 1e6)
     :cljs (js/performance.now)))

(defn- elapsed-ms
  "The milliseconds that calling `f` takes."
  [f]
  (let [start (now-ms)]
    (f)
    (- (now-ms) start)))

(defn- median
  [xs]
  (nth (sort xs) (quot (count xs) 2)))

(defn measure
  "The median milliseconds of each function of `fs`, a map of name to
  function, over `rounds` rounds after `warmup` rounds."
  [fs warmup rounds]
  (dotimes [_ warmup]
    (run! (fn [[_ f]] (f)) fs))
  (let [times (vec (repeatedly rounds #(update-vals fs elapsed-ms)))]
    (into {} (for [k (keys fs)] [k (median (map k times))]))))

(defn- format-row
  [& args]
  (let [pattern "| %-28s | %7.2f MB | %8.1f ms | %10.1f ms | %4.0f%% | %8.1f ms |"]
    #?(:clj  (String/format java.util.Locale/ROOT pattern (to-array args))
       :cljs (apply gstring/format pattern args))))

(defn run
  "Print the time of html-pieces/parse, of mf/parse on its tree, and of
  mf/parse on the HTML, for each fixture, by these keys of `opts`:

  - :warmup, the rounds before the timing, 10 by default
  - :rounds, the rounds that are timed, 21 by default"
  [{:keys [warmup rounds] :or {warmup 10 rounds 21}}]
  #?(:clj (fetch! fixtures))
  (println "| Page                         |       Size |       Parse | Microformats | Share |        Both |")
  (println "| :--------------------------- | ---------: | ----------: | -----------: | ----: | ----------: |")
  (doseq [fixture fixtures]
    (let [s    (read-fixture fixture)
          tree (html-pieces/parse s)
          url  (:url fixture)
          {:keys [parse microformats whole]}
          (measure {:parse        #(html-pieces/parse s)
                    :microformats #(mf/parse tree url)
                    :whole        #(mf/parse s url)}
                   warmup
                   rounds)]
      (println (format-row (:name fixture) (/ (size fixture) 1e6) parse
                           microformats (* 100 (/ microformats parse)) whole)))))

#?(:cljs
   (defn main
     "Run the benchmarks in Node."
     [& _]
     (run {})))

(comment
  (run {:warmup 3 :rounds 5})
  #_.)
