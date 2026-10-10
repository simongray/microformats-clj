(ns dk.simongray.microformats.url-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.microformats.url :as url]))

;; RFC 3986, 5.4, but for the two references that the URL Standard reads
;; otherwise: //g gets the path /, and http:g is relative
(def rfc-3986-examples
  {"g:h"           "g:h"
   "g"             "http://a/b/c/g"
   "./g"           "http://a/b/c/g"
   "g/"            "http://a/b/c/g/"
   "/g"            "http://a/g"
   "//g"           "http://g/"
   "?y"            "http://a/b/c/d;p?y"
   "g?y"           "http://a/b/c/g?y"
   "#s"            "http://a/b/c/d;p?q#s"
   "g#s"           "http://a/b/c/g#s"
   "g?y#s"         "http://a/b/c/g?y#s"
   ";x"            "http://a/b/c/;x"
   "g;x"           "http://a/b/c/g;x"
   "g;x?y#s"       "http://a/b/c/g;x?y#s"
   ""              "http://a/b/c/d;p?q"
   "."             "http://a/b/c/"
   "./"            "http://a/b/c/"
   ".."            "http://a/b/"
   "../"           "http://a/b/"
   "../g"          "http://a/b/g"
   "../.."         "http://a/"
   "../../"        "http://a/"
   "../../g"       "http://a/g"
   "../../../g"    "http://a/g"
   "../../../../g" "http://a/g"
   "/./g"          "http://a/g"
   "/../g"         "http://a/g"
   "g."            "http://a/b/c/g."
   ".g"            "http://a/b/c/.g"
   "g.."           "http://a/b/c/g.."
   "..g"           "http://a/b/c/..g"
   "./../g"        "http://a/b/g"
   "./g/."         "http://a/b/c/g/"
   "g/./h"         "http://a/b/c/g/h"
   "g/../h"        "http://a/b/c/h"
   "g;x=1/./y"     "http://a/b/c/g;x=1/y"
   "g;x=1/../y"    "http://a/b/c/y"
   "g?y/./x"       "http://a/b/c/g?y/./x"
   "g?y/../x"      "http://a/b/c/g?y/../x"
   "g#s/./x"       "http://a/b/c/g#s/./x"
   "g#s/../x"      "http://a/b/c/g#s/../x"
   "http:g"        "http://a/b/c/g"})

(deftest the-examples-of-rfc-3986
  (doseq [[ref expected] rfc-3986-examples]
    (is (= expected (url/resolve "http://a/b/c/d;p?q" ref)) ref)))

(deftest what-the-url-standard-changes
  (is (= "http://example.com/" (url/resolve "http://example.com" "")))
  (is (= "http://example.com/a/c%20d"
         (url/resolve "http://example.com/blog/" "HTTP://Example.COM:80/a/./b/../c d")))
  (is (= "http://a/ab" (url/resolve "http://a/b" " \t/a\nb ")))
  (is (= "http://x/y" (url/resolve "http://a/b" "\\\\x\\y")))
  (is (= "https://x.org/p" (url/resolve "http://a/b" "https:x.org/p")))
  (is (= "http://x/y" (url/resolve "http://a/b" "///x/y")))
  (is (= "http://a/c" (url/resolve "http://a/b" "%2e%2E/c")))
  (is (= "http://a/b/c?q=a%20b%27c" (url/resolve "http://a/b/c" "?q=a b'c")))
  (is (= "http://a/%C3%A9?%C3%A9#%C3%A9" (url/resolve "http://a/b" "é?é#é")))
  (is (= "http://a/%F0%9F%98%80" (url/resolve "http://a" "😀")))
  (is (= "http://u:p@x/" (url/resolve "http://a/b" "http://u:p@x:0080/")))
  (is (= "foo://h/r" (url/resolve "foo://h/p/q" "../r"))))

(deftest opaque-and-invalid-urls
  (is (= "mailto:jane@example.com#top" (url/resolve "mailto:jane@example.com" "#top")))
  (is (nil? (url/resolve "mailto:jane@example.com" "x")))
  (is (= "javascript:alert(1)" (url/resolve "http://a/b" "javascript:alert(1)")))
  (is (nil? (url/resolve "http://a/b" "http://")))
  (is (nil? (url/resolve "http://a/b" "http://x:y/")))
  (is (nil? (url/resolve nil "relative")))
  (is (= "https://x.y/" (url/resolve nil "HTTPS://X.y"))))
