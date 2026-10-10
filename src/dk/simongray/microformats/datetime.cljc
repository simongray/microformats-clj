(ns ^:no-doc dk.simongray.microformats.datetime
  "Dates and times read by the date and time parsing rules of the
  value-class-pattern: https://microformats.org/wiki/value-class-pattern

  A date is YYYY-MM-DD or YYYY-DDD. A time is HH:MM, with optional seconds
  and a time zone, or a time on the 12-hour clock, e.g. 7pm or 07:00 a.m.
  A time zone is Z, or an offset such as -08:00, -0800 or -08."
  (:require [dk.simongray.microformats.html :as html]))

(def ^:private date
  "(\\d{4}-\\d{2}-\\d{2}|\\d{4}-\\d{3})")

(def ^:private zone
  "(Z|z|[+-]\\d{1,2}(?::?\\d{2})?)")

(def ^:private clock
  "(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

;; the hours, the optional minutes and seconds, and a or p
(def ^:private twelve-hour-clock
  "(\\d{1,2})(?::(\\d{2})(?::(\\d{2}))?)?[\\t\\n\\f\\r ]*([aApP])\\.?[mM]\\.?")

(def ^:private date-re
  (re-pattern date))

(def ^:private time-re
  (re-pattern (str clock zone "?")))

(def ^:private twelve-hour-re
  (re-pattern twelve-hour-clock))

(def ^:private zone-re
  (re-pattern zone))

(def ^:private date-time-re
  (re-pattern (str date "[Tt ](.+)")))

(defn- two-digits
  [s]
  (cond->> s (= 1 (count s)) (str "0")))

(defn- normalized-zone
  "The time zone `s` as Z or as an offset without a colon, e.g. -0800."
  [s]
  (if (#{"Z" "z"} s)
    "Z"
    (let [[_ sign hours minutes] (re-matches #"([+-])(\d{1,2}):?(\d{2})?" s)]
      (str sign (two-digits hours) minutes))))

(defn- parsed-time
  "The time `s` as a map of :time on the 24-hour clock, as precise as it's
  written, and :zone, or nil when it isn't a time."
  [s]
  (if-let [[_ h m sec z] (re-matches time-re s)]
    (cond-> {:time (str (two-digits h) ":" m (when sec (str ":" sec)))}
      z (assoc :zone (normalized-zone z)))
    (when-let [[_ h m sec ap] (re-matches twelve-hour-re s)]
      (let [h  (parse-long h)
            pm (#{"p" "P"} ap)
            h  (cond
                 (and pm (< h 12))       (+ h 12)
                 (and (not pm) (= 12 h)) 0
                 :else                   h)]
        (when (<= h 24)
          {:time (str (two-digits (str h)) ":" (or m "00") (when sec (str ":" sec)))})))))

(defn parsed
  "The text `s` as a map of the parts that it has of :date, :time and
  :zone, or nil when it's none of a date, a time, a date and a time, or a
  time zone."
  [s]
  (cond
    (re-matches date-re s)
    {:date s}

    (re-matches zone-re s)
    {:zone (normalized-zone s)}

    :else
    (or (parsed-time s)
        (when-let [[_ d t] (re-matches date-time-re s)]
          (some-> (parsed-time t) (assoc :date d))))))

;; most values of dt- properties start with a year, and need no other test
(defn time-only
  "The text `s` as a map of :time and :zone when it's a time without a
  date, or else nil."
  [s]
  (when-not (re-find #"^\d{4}" s)
    (parsed-time s)))

(defn- add-part
  "The parts `acc` with the parts `x` of the next value added, by the
  rules of the value-class-pattern: the first date, the first time and the
  first time zone win, and a date with a time counts only when it comes
  before any other date or time."
  [acc x]
  (let [{:keys [date time zone]} x]
    (cond
      (and date time)
      (if (or (:date acc) (:time acc))
        acc
        (cond-> (assoc acc :date date :time time)
          (and zone (not (:zone acc))) (assoc :zone zone)))

      date
      (cond-> acc (not (:date acc)) (assoc :date date))

      time
      (if (:time acc)
        acc
        (cond-> (assoc acc :time time)
          (and zone (not (:zone acc))) (assoc :zone zone)))

      zone
      (cond-> acc (not (:zone acc)) (assoc :zone zone))

      :else
      acc)))

(defn combined
  "The date and time that the texts `values` of the value elements give,
  as a map of :date, :time and :zone, or nil when they give no date and no
  time."
  [values]
  (let [parts (reduce add-part {} (keep (comp parsed html/strip) values))]
    (when (or (:date parts) (:time parts))
      parts)))

(defn text
  "The date and time `parts` as text, e.g. 2009-06-26 19:00-0800. A zone is
  written only after a time."
  [{:keys [date time zone]}]
  (str date
       (when (and date time) " ")
       time
       (when time zone)))

(defn date-of
  "The date at the start of the text `s`, or nil."
  [s]
  (when (string? s)
    (second (re-find #"^(\d{4}-\d{2}-\d{2}|\d{4}-\d{3})(?!\d)" s))))
