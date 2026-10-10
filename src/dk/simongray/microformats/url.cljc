(ns dk.simongray.microformats.url
  "URLs resolved against a base URL, by RFC 3986, section 5, with the
  changes of the WHATWG URL Standard that browsers make, as of 2026-10-10:

  - tabs and line breaks are dropped, and so are the control characters
    and spaces at the ends
  - in a URL of a special scheme, e.g. http, a backslash is a slash, the
    path is at least /, the host is in lower case and a default port is
    left out
  - a URL of the scheme of its base and no authority is relative, e.g.
    http:g, and a URL of a special scheme reads its authority past any
    number of slashes
  - a dot segment can be percent-encoded, e.g. %2e%2e
  - the characters of the percent-encode sets are percent-encoded as UTF-8

  TODO: the host parser of the URL Standard, which maps a domain by IDNA,
  reads the numbers of an IPv4 address in any base, compresses an IPv6
  address and rejects the forbidden code points, and the drive letters of
  file URLs."
  (:refer-clojure :exclude [resolve])
  (:require [clojure.string :as str]))

;; URL Standard, "Special scheme"
(def default-ports
  "The special schemes, each with its default port, or nil."
  {"ftp" "21" "file" nil "http" "80" "https" "443" "ws" "80" "wss" "443"})

(defn special?
  "Whether `scheme` is one of the special schemes of the URL Standard."
  [scheme]
  (contains? default-ports scheme))

;; URL Standard, "Percent-encoded bytes": each set is a table of the ASCII
;; codes that it holds, and every set holds all above ~ too
(defn- encode-set
  "The percent-encode set of the C0 controls and the ASCII `codes`."
  [codes]
  (let [table #?(:clj  (boolean-array 128)
                 :cljs (.fill (make-array 128) false))]
    (doseq [c (concat (range 0x20) codes)]
      (aset table c true))
    table))

(def c0-control-set
  "The C0 control percent-encode set."
  (encode-set []))

(def fragment-set
  "The fragment percent-encode set."
  (encode-set [0x20 0x22 0x3C 0x3E 0x60]))

(def query-set
  "The query percent-encode set."
  (encode-set [0x20 0x22 0x23 0x3C 0x3E]))

(def special-query-set
  "The special-query percent-encode set."
  (encode-set [0x20 0x22 0x23 0x3C 0x3E 0x27]))

(def path-set
  "The path percent-encode set."
  (encode-set [0x20 0x22 0x23 0x3C 0x3E 0x3F 0x5E 0x60 0x7B 0x7D]))

(def userinfo-set
  "The userinfo percent-encode set."
  (encode-set [0x20 0x22 0x23 0x3C 0x3E 0x3F 0x5E 0x60 0x7B 0x7D
               0x2F 0x3A 0x3B 0x3D 0x40 0x5B 0x5C 0x5D 0x7C]))

(defn- utf-8
  "The bytes of the code point `c` in UTF-8."
  [c]
  (cond
    (< c 0x80)    [c]
    (< c 0x800)   [(bit-or 0xC0 (bit-shift-right c 6))
                   (bit-or 0x80 (bit-and c 0x3F))]
    (< c 0x10000) [(bit-or 0xE0 (bit-shift-right c 12))
                   (bit-or 0x80 (bit-and (bit-shift-right c 6) 0x3F))
                   (bit-or 0x80 (bit-and c 0x3F))]
    :else         [(bit-or 0xF0 (bit-shift-right c 18))
                   (bit-or 0x80 (bit-and (bit-shift-right c 12) 0x3F))
                   (bit-or 0x80 (bit-and (bit-shift-right c 6) 0x3F))
                   (bit-or 0x80 (bit-and c 0x3F))]))

(defn- percent-encoded-byte
  [b]
  (let [hex (str/upper-case #?(:clj  (Integer/toHexString b)
                               :cljs (.toString b 16)))]
    (str "%" (when (< b 0x10) "0") hex)))

(defn- code-unit
  ^long [s ^long i]
  #?(:clj  (long (int (.charAt ^String s (int i))))
     :cljs (.charCodeAt s i)))

(defn- code-point-at
  "The code point at `i` of the text `s` and its length in code units. A
  lone surrogate is U+FFFD, as UTF-8 encodes it."
  [s i]
  (let [hi (code-unit s i)]
    (cond
      (and (<= 0xD800 hi 0xDBFF) (< (inc i) (count s))
           (<= 0xDC00 (code-unit s (inc i)) 0xDFFF))
      [(+ 0x10000
          (bit-shift-left (- hi 0xD800) 10)
          (- (code-unit s (inc i)) 0xDC00))
       2]

      (<= 0xD800 hi 0xDFFF)
      [0xFFFD 1]

      :else
      [hi 1])))

(defn- in-set?
  [^booleans set ^long c]
  (or (< 0x7E c) (aget set c)))

(defn- encoded-from
  "The text `s` with each code point of the percent-encode `set` from `i` on
  percent-encoded as UTF-8."
  [set s i]
  (loop [i i out [(subs s 0 i)]]
    (if (< i (count s))
      (let [[c n] (code-point-at s i)]
        (recur (+ i n)
               (if (in-set? set c)
                 (into out (map percent-encoded-byte) (utf-8 c))
                 (conj out (subs s i (+ i n))))))
      (apply str out))))

;; most text needs no percent-encoding, which a loop finds fastest
(defn percent-encode
  "The text `s` with each code point of the percent-encode `set`
  percent-encoded as UTF-8."
  [set s]
  (let [n (count s)]
    (loop [i 0]
      (cond
        (== i n)                       s
        (in-set? set (code-unit s i)) (encoded-from set s i)
        :else                          (recur (inc i))))))

;; URL Standard, "Concepts": a URL is read without its tabs and line breaks,
;; and without the C0 controls and spaces at its ends
(defn- cleaned
  [s]
  (if (or (= "" s)
          (<= (code-unit s 0) 0x20)
          (<= (code-unit s (dec (count s))) 0x20)
          (str/includes? s "\t")
          (str/includes? s "\n")
          (str/includes? s "\r"))
    (-> s
        (str/replace #"^[\x00-\x20]+|[\x00-\x20]+$" "")
        (str/replace #"[\t\n\r]" ""))
    s))

;; RFC 3986, appendix B, with the scheme read separately
(def ^:private reference
  #"(?s)^(?://([^/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#(.*))?$")

(defn- alpha?
  [^long c]
  (or (and (<= 0x41 c) (<= c 0x5A))
      (and (<= 0x61 c) (<= c 0x7A))))

(defn- scheme-code?
  [^long c]
  (or (alpha? c)
      (and (<= 0x30 c) (<= c 0x39))
      (== c 0x2B) (== c 0x2D) (== c 0x2E)))

;; str/lower-case follows the default locale on the JVM, which in Turkish
;; makes I a dotless i
(defn- lower-case
  [s]
  #?(:clj  (.toLowerCase ^String s java.util.Locale/ROOT)
     :cljs (.toLowerCase s)))

(defn- split-scheme
  "The scheme of the URL `s` in lower case and the rest of it, or nil and
  `s` when it has none."
  [s]
  (let [n     (count s)
        colon (when (and (pos? n) (alpha? (code-unit s 0)))
                (loop [i 1]
                  (when (< i n)
                    (let [c (code-unit s i)]
                      (cond
                        (== 0x3A c)       i
                        (scheme-code? c)  (recur (inc i))
                        :else             nil)))))]
    (if colon
      [(lower-case (subs s 0 colon)) (subs s (inc colon))]
      [nil s])))

(defn- authority
  "The userinfo, the host and the port of the `authority` of a URL with
  `scheme`, or nil when it isn't valid."
  [scheme authority]
  (let [at       (str/last-index-of authority "@")
        userinfo (when at (subs authority 0 at))
        hostport (if at (subs authority (inc at)) authority)
        [_ host port] (re-matches #"(\[[^\]]*\]|[^:]*)(?::(.*))?" hostport)
        host     (cond-> host (special? scheme) lower-case)
        port     (when (seq port) (str/replace port #"^0+(?=\d)" ""))]
    (when (and host
               (or (nil? port) (re-matches #"\d+" port))
               (or (seq host) (not (special? scheme)) (= "file" scheme)))
      {:userinfo (when userinfo
                   (->> (str/split userinfo #":" 2)
                        (map #(percent-encode userinfo-set %))
                        (str/join ":")))
       :host     host
       :port     (when (not= port (get default-ports scheme)) port)})))

(defn- parts
  "The parts of the reference `s` to a URL with `scheme`, by RFC 3986,
  with an authority past any number of slashes when the scheme is special
  and `authority?`."
  [scheme s authority?]
  (let [special (special? scheme)
        s       (cond-> s
                  (and special (str/includes? s "\\")) (str/replace "\\" "/"))
        s       (if (and special
                         authority?
                         (not (re-find #"^//[^/]" s)))
                  (str "//" (str/replace s #"^/*" ""))
                  s)
        [_ auth path query fragment] (re-matches reference s)]
    (cond-> {:path path :query query :fragment fragment}
      auth (assoc :authority auth))))

(defn- dot?
  [segment]
  (contains? #{"." "%2e"} (lower-case segment)))

(defn- dot-dot?
  [segment]
  (contains? #{".." ".%2e" "%2e." "%2e%2e"} (lower-case segment)))

;; RFC 3986, 5.2.4, "Remove Dot Segments", by the path state of the URL
;; Standard
(defn- normalized-path
  "The absolute `path` without its dot segments, and with its segments
  percent-encoded."
  [path]
  ;; most paths have no dot segment, nor a percent-encoded one
  (if-not (or (str/includes? path ".") (str/includes? path "%"))
    (percent-encode path-set path)
    (let [segments (rest (str/split path #"/" -1))
          n        (count segments)]
      (->> (map-indexed vector segments)
           (reduce (fn [out [i segment]]
                     (let [last? (= i (dec n))]
                       (cond
                         (dot-dot? segment) (cond-> (if (seq out) (pop out) out)
                                              last? (conj ""))
                         (dot? segment)     (cond-> out last? (conj ""))
                         :else              (conj out (percent-encode path-set segment)))))
                   [])
           (str/join "/")
           (str "/")))))

;; RFC 3986, 5.2.3, "Merge Paths"
(defn- merged-path
  [base path]
  (if (and (:host base) (= "" (:path base)))
    (str "/" path)
    (str (subs (:path base) 0 (inc (or (str/last-index-of (:path base) "/") -1)))
         path)))

(defn- target
  "The URL that the parts `ref` of a relative reference give against the
  URL `base`, by RFC 3986, 5.2.2, or nil when there is none."
  [base ref]
  (cond
    (:opaque? base)
    (when (and (nil? (:authority ref)) (= "" (:path ref)) (nil? (:query ref)))
      (assoc base :fragment (:fragment ref)))

    (:authority ref)
    (when-let [auth (authority (:scheme base) (:authority ref))]
      (merge {:scheme (:scheme base)} auth (dissoc ref :authority)))

    (= "" (:path ref))
    (assoc base
           :query    (if (:query ref) (:query ref) (:query base))
           :fragment (:fragment ref))

    :else
    (assoc base
           :path     (if (str/starts-with? (:path ref) "/")
                       (:path ref)
                       (merged-path base (:path ref)))
           :query    (:query ref)
           :fragment (:fragment ref))))

(defn- absolute
  "The URL of the parts `ref` that has a `scheme`, or nil when its
  authority isn't valid."
  [scheme ref]
  (if-let [auth (:authority ref)]
    (some->> (authority scheme auth) (merge {:scheme scheme} (dissoc ref :authority)))
    (assoc ref
           :scheme  scheme
           :opaque? (not (str/starts-with? (:path ref) "/")))))

(defn- normalized
  "The URL `u` with its path and query normalized as the URL Standard
  does, or nil when a special scheme lacks a host."
  [{:keys [scheme host path opaque?] :as u}]
  (when-not (and (special? scheme) (nil? host))
    (cond-> u
      (and (not opaque?) (or host (seq path)))
      (assoc :path (if (and (not (special? scheme)) (= "" path))
                     path
                     (normalized-path (if (str/starts-with? path "/")
                                        path
                                        (str "/" path)))))

      opaque?
      (update :path #(percent-encode c0-control-set %))

      (:query u)
      (update :query #(percent-encode (if (special? scheme)
                                        special-query-set
                                        query-set)
                                      %))

      (:fragment u)
      (update :fragment #(percent-encode fragment-set %)))))

(defn- serialized
  "The URL `u` as text, by the URL serializer of the URL Standard."
  [{:keys [scheme userinfo host port path query fragment]}]
  (str scheme ":"
       (when host
         (str "//" (when userinfo (str userinfo "@")) host (when port (str ":" port))))
       ;; a path that starts with // would read as an authority
       (when (and (nil? host) (str/starts-with? path "//")) "/.")
       path
       (when query (str "?" query))
       (when fragment (str "#" fragment))))

(defn- parse
  "The URL `s` against the parsed URL `base`, or nil."
  [base s]
  (let [[scheme rest] (split-scheme (cleaned s))
        same?         (and scheme (special? scheme) (= scheme (:scheme base)))]
    (cond
      ;; http:g against an http base is relative, as in a browser
      (and same? (not (re-find #"^[/\\]{2}" rest)))
      (target base (parts scheme rest false))

      scheme
      (absolute scheme (parts scheme rest true))

      base
      (let [authority? (re-find #"^[/\\]{2}" rest)]
        (target base (parts (:scheme base) rest authority?))))))

(defn- host-code?
  [^long c]
  (or (and (<= 0x61 c) (<= c 0x7A))
      (and (<= 0x30 c) (<= c 0x39))
      (== c 0x2E)
      (== c 0x2D)))

(defn- path-code?
  "Whether the code `c` can be in the path, the query or the fragment of a
  URL of a special scheme as it's serialized, after the code `before`, with
  no dot segment."
  [^long before ^long c]
  (and (<= 0x21 c)
       (<= c 0x7E)
       (case (int c)
         (0x22 0x27 0x3C 0x3E 0x5C 0x5E 0x60 0x7B 0x7D) false
         true)
       (not (and (== 0x2F before) (or (== 0x2E c) (== 0x25 c))))))

;; most absolute URLs of a page are written as they're serialized, which
;; one pass over them tells
(defn- normal?
  "Whether the text `s` is an http or https URL as the URL Standard
  serializes it, with a host of lower-case letters, digits, dots and
  hyphens, a path and no port."
  [s]
  (let [n     (count s)
        start (cond
                (str/starts-with? s "https://") 8
                (str/starts-with? s "http://")  7)]
    (when start
      (loop [i start host? true]
        (if (== i n)
          (not host?)
          (let [c (code-unit s i)]
            (cond
              (and host? (== 0x2F c))              (and (< start i) (recur (inc i) false))
              host?                                (and (host-code? c) (recur (inc i) true))
              (path-code? (code-unit s (dec i)) c) (recur (inc i) false)
              :else                                false)))))))

(defn resolver
  "A function of a URL that resolves it against the absolute URL `base`, as
  resolve does, which reads the base only once."
  [base]
  (let [b (some->> base (parse nil) normalized)]
    (fn [s]
      (if (normal? s)
        s
        (some-> (parse b s) normalized serialized)))))

(defn resolve
  "The URL `s` resolved against the absolute URL `base`, and normalized as
  a browser normalizes it, or nil when it isn't valid. With no `base`, only
  an absolute URL is valid.

      (resolve \"http://a/b/c/d;p?q\" \"../g\")
      ;; => \"http://a/b/g\""
  [base s]
  ((resolver base) s))

(comment
  (resolve "http://example.com" "bios/mitchell-baker/")
  (resolve "http://a/b/c/d;p?q" "http:g")
  (resolve "http://example.com/blog/" "HTTP://Example.COM:80/a/./b/../c d")
  (resolve "mailto:jane@example.com" "#top")
  #_.)
