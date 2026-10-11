(ns dk.simongray.microformats-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.html-pieces :as html-pieces]
            [dk.simongray.microformats :as mf]))

(def post
  (str "<html><head>"
       "<link rel=\"webmention\" href=\"\">"
       "<link rel=\"me authn\" href=\"https://github.com/jane\">"
       "<template><link rel=\"me\" href=\"/not-me\"></template>"
       "</head><body>"
       "<article class=\"h-entry\">"
       "<h1 class=\"p-name\">Hello</h1>"
       "<a class=\"u-url\" href=\"/posts/hello\">"
       "<time class=\"dt-published\" datetime=\"2026-10-10T12:00:00+02:00\">today</time></a>"
       "<a class=\"p-author h-card\" href=\"/\"><img src=\"/jane.jpg\" alt=\"\">Jane</a>"
       "<a class=\"u-in-reply-to\" href=\"https://example.org/post\">a post</a>"
       "<div class=\"e-content\"> <p>Hi <a href=\"/about#me\">me</a></p> </div>"
       "</article></body></html>"))

(def url
  "https://jane.example/posts/hello")

(deftest an-entry
  (let [{:keys [items rels rel-urls]} (mf/parse post url)
        {:keys [properties]}           (first items)]
    (is (= ["h-entry"] (:type (first items))))
    (is (= ["Hello"] (:name properties)))
    (is (= ["https://jane.example/posts/hello"] (:url properties)))
    (is (= ["2026-10-10T12:00:00+02:00"] (:published properties)))
    (is (= ["https://example.org/post"] (:in-reply-to properties)))
    (is (= {:type       ["h-card"]
            :properties {:name  ["Jane"]
                         :url   ["https://jane.example/"]
                         :photo [{:value "https://jane.example/jane.jpg" :alt ""}]}
            :value      "Jane"}
           (first (:author properties))))
    (is (= {:html   "<p>Hi <a href=\"https://jane.example/about#me\">me</a></p>"
            :value  "Hi me"
            :hiccup '([:p {} "Hi " [:a {:href "https://jane.example/about#me"} "me"]])}
           (first (:content properties))))
    (is (= {"webmention" ["https://jane.example/posts/hello"]
            "me"         ["https://github.com/jane"]
            "authn"      ["https://github.com/jane"]}
           rels))
    (is (= {:rels ["authn" "me"]} (get rel-urls "https://github.com/jane")))))

(deftest html-or-hiccup
  (is (= (mf/parse post url)
         (mf/parse (html-pieces/parse post) url)))
  (is (= (mf/parse "<p class=\"h-card\">Jane</p>" nil)
         (mf/parse [:p {:class "h-card"} "Jane"] nil)
         (mf/parse [[:p {:class "h-card"} "Jane"]] nil))))

(defn- text-values
  "The name and the text of the content of the first item that the HTML `s`
  parses to, by `opts`."
  [s opts]
  (let [{:keys [name content]} (get-in (mf/parse s url opts) [:items 0 :properties])]
    [(first name) (:value (first content))]))

(def blocks
  (str "<div class=\"h-entry\">"
       "<p class=\"p-name\">(for 30 days).<br><br>In the app</p>"
       "<div class=\"e-content\">"
       "<p>One.</p><p>Two<br>three.</p><ul><li>a</li><li>b</li></ul><div>c</div>"
       "</div></div>"))

(def indented
  "<div class=\"h-entry\"><p class=\"p-name\">\n  Last week the\n    community met</p></div>")

(deftest text-values-with-breaks
  (is (= ["(for 30 days).\n\nIn the app" "One.\nTwo\nthree.\na\nb\nc"]
         (text-values blocks {})))
  (is (= ["Last week the\n    community met" nil]
         (text-values indented {}))))

(deftest text-values-with-whitespace-collapsed
  (is (= ["(for 30 days).\n\nIn the app" "One.\nTwo\nthree.abc"]
         (text-values blocks {:collapse-whitespace? true})))
  (is (= ["Last week the community met" nil]
         (text-values indented {:collapse-whitespace? true}))))

(deftest tags-as-they-are-written
  (is (= "<x-a.b>t</x-a.b>"
         (-> (mf/parse "<div class=\"h-entry\"><div class=\"e-content\"><x-a.b>t</x-a.b></div></div>" url)
             (get-in [:items 0 :properties :content 0 :html])))))

(deftest rels-in-lower-case
  (let [{:keys [rels rel-urls]} (mf/parse "<link rel=\"Webmention ME\" href=\"/wm\">" url)]
    (is (= {"webmention" ["https://jane.example/wm"]
            "me"         ["https://jane.example/wm"]}
           rels))
    (is (= ["me" "webmention"] (get-in rel-urls ["https://jane.example/wm" :rels])))))

(deftest the-base-url
  (let [base "<base href=\"/blog/\"><base href=\"/other/\"><a class=\"h-card\" href=\"jane\">Jane</a>"]
    (is (= ["https://jane.example/blog/jane"]
           (get-in (mf/parse base url) [:items 0 :properties :url]))))
  (is (= ["jane"]
         (get-in (mf/parse "<a class=\"h-card\" href=\"jane\">Jane</a>" nil)
                 [:items 0 :properties :url])))
  (is (= ["https://jane.example/posts/jane"]
         (get-in (mf/parse (str "<template><base href=\"/blog/\"></template>"
                                "<a class=\"h-card\" href=\"jane\">Jane</a>")
                           url)
                 [:items 0 :properties :url]))))
