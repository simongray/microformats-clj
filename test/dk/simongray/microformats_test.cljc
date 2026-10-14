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
  "The name and content text of the first item in the HTML `s`, parsed
  with `opts`."
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

(deftest rels-alone
  (is (= (select-keys (mf/parse post url) [:rels :rel-urls])
         (mf/rels post url)
         (mf/rels (html-pieces/parse post) url))
      "the same as parse returns, from HTML or Hiccup"))

(deftest reading-the-items
  (let [mf2     (mf/parse (str "<div class=\"h-entry\"><div class=\"h-card\">Ann</div>"
                               "<span class=\"p-author p-organizer h-card\">Bo</span>"
                               "<a class=\"u-in-reply-to h-cite\" href=\"https://example.org/post\">"
                               "<span class=\"p-name\">Their post</span></a>"
                               "<img class=\"u-photo\" src=\"/a.jpg\" alt=\"A cat\"></div>")
                          url)
        [entry] (:items mf2)]
    (is (= [["h-entry"] ["h-card"] ["h-card"] ["h-cite"]] (map :type (mf/items mf2)))
        "every microformat in document order, though children are kept apart from properties")
    (is (= ["Ann" "Bo"] (map #(mf/property % :name) (mf/items mf2 "h-card")))
        "only those of a type, each once, even when it's the value of two properties")
    (is (= "Bo" (mf/property entry :author)) "the value of an embedded microformat")
    (is (= "https://jane.example/a.jpg" (mf/property entry :photo)) "of an image with alt text")
    (is (= ["https://example.org/post"] (mf/urls entry :in-reply-to)) "the url of an h-cite")
    (is (= ["https://example.org/post"] (mf/urls {:properties {:in-reply-to ["HTTPS://Example.org:443/post" "a post"]}}
                                                 :in-reply-to))
        "only the absolute URLs, normalized")))

(deftest the-representative-card
  (let [card #(mf/representative-card (mf/parse % url) url)]
    (is (= ["Jane"]
           (get-in (card (str "<p class=\"h-card\"><a class=\"p-name u-url\" href=\"/joe\">Joe</a></p>"
                              "<p class=\"h-card\"><a class=\"p-name u-url u-uid\" href=\"HTTPS://Jane.Example/posts/hello\">Jane</a></p>"))
                   [:properties :name]))
        "the first whose url and uid are the page's, compared as URLs")
    (is (= ["Jane"]
           (get-in (card (str "<p class=\"h-card\"><a class=\"p-name u-url\" href=\"/joe\">Joe</a></p>"
                              "<div class=\"h-feed\"><p class=\"p-author h-card\">"
                              "<a class=\"p-name u-url\" href=\"https://jane.example/\">Jane</a></p></div>"
                              "<a rel=\"me\" href=\"https://jane.example/\">me</a>"))
                   [:properties :name]))
        "or else the first whose url is a rel=me link of the page, even a nested one")
    (is (= ["Jane"]
           (get-in (card "<p class=\"h-card\"><a class=\"p-name u-url\" href=\"\">Jane</a></p>")
                   [:properties :name]))
        "or else the only one, with the page's url")
    (is (nil? (card (str "<p class=\"h-card\"><a class=\"p-name u-url\" href=\"\">Jane</a></p>"
                         "<p class=\"h-card\">Joe</p>")))
        "but not one of two")))

(deftest the-author-of-an-entry
  (let [author #(let [mf2 (mf/parse % url)]
                  (mf/author mf2 url (first (mf/items mf2 "h-entry"))))]
    (is (= {:card {:type       ["h-card"]
                   :properties {:name ["Jane"] :url ["https://jane.example/"]}
                   :value      "Jane"}}
           (author "<article class=\"h-entry\"><a class=\"p-author h-card\" href=\"/\">Jane</a></article>"))
        "an h-card")
    (is (= {:page "https://jane.example/"}
           (author "<article class=\"h-entry\"><a class=\"u-author\" href=\"/\">Jane</a></article>"))
        "the URL of the author's page")
    (is (= {:card {:type ["h-card"] :properties {:name ["Jane"]}}}
           (author "<article class=\"h-entry\"><span class=\"p-author\">Jane</span></article>"))
        "a name alone")
    (is (= {:page "https://jane.example/"}
           (author (str "<div class=\"h-feed\"><a class=\"u-author\" href=\"/\">Jane</a>"
                        "<article class=\"h-entry\"><p class=\"p-name\">Hi</p></article></div>")))
        "the author of the feed")
    (is (= {:page "https://jane.example/about"}
           (author (str "<a rel=\"author\" href=\"/about\">Jane</a>"
                        "<article class=\"h-entry\"><a class=\"u-url\" href=\"\">Hi</a></article>")))
        "a rel=author on the entry's permalink page")
    (is (nil? (author (str "<a rel=\"author\" href=\"/about\">Jane</a>"
                           "<article class=\"h-entry\"><a class=\"u-url\" href=\"/other\">Hi</a></article>")))
        "but not on another page"))
  (let [page "https://jane.example/"
        mf2  (mf/parse "<p class=\"h-card\"><a class=\"u-url p-name\" href=\"/\">Jane here</a></p>" url)]
    (is (= ["Jane there"]
           (get-in (mf/author-card mf2 page (mf/parse (str "<p class=\"h-card\"><a class=\"p-name u-url u-uid\" href=\"/\">"
                                                           "Jane there</a></p>")
                                                      page))
                   [:properties :name]))
        "the h-card on the author's page")
    (is (= ["Jane here"] (get-in (mf/author-card mf2 page nil) [:properties :name]))
        "or else the h-card on the entry's page whose url is the author's page")))

(def wordpress-post
  (str "<article id=\"post-1\" class=\"post-1 post hentry\">"
       "<h1 class=\"entry-title\"><a href=\"/hello/\" rel=\"bookmark\">Hello</a></h1>"
       "<div class=\"entry-content\"><p>Hi.</p></div>"
       "<footer>In <a href=\"/category/news/\" rel=\"category tag\">News</a> "
       "by <span class=\"author vcard\"><a class=\"url fn n\" href=\"/author/jane/\">Jane</a></span>"
       "</footer></article>"))

(deftest a-backcompat-entry
  (is (= [{:type       ["h-entry"]
           :properties {:name     ["Hello"]
                        :url      ["https://jane.example/hello/"]
                        :content  [{:html   "<p>Hi.</p>"
                                    :value  "Hi."
                                    :hiccup '([:p {} "Hi."])}]
                        :category ["news"]
                        :author   [{:type       ["h-card"]
                                    :properties {:url  ["https://jane.example/author/jane/"]
                                                 :name ["Jane"]}
                                    :value      "Jane"}]}
           :id         "post-1"}]
         (:items (mf/parse wordpress-post url)))))

(def wordpress-theme-post
  (str "<article class=\"hentry\">"
       "<a href=\"/hello/\" rel=\"bookmark\"><time class=\"entry-date\" datetime=\"2014-01-01T00:00:00+00:00\">1 January</time></a>"
       "<a href=\"/author/jane/\" rel=\"author\">Jane</a>"
       "</article>"))

(deftest proposed-backcompat
  (let [properties #(get-in (mf/parse %1 url %2) [:items 0 :properties])]
    (is (= {:url ["https://jane.example/hello/"]}
           (properties wordpress-theme-post {})))
    (is (= {:url       ["https://jane.example/hello/"]
            :published ["2014-01-01T00:00:00+00:00"]
            :author    ["https://jane.example/author/jane/"]}
           (properties wordpress-theme-post {:proposed-backcompat? true})))
    (is (= ["2026-10-10"]
           (:published (properties "<p class=\"hentry\"><time class=\"published\">2026-10-10</time><time class=\"entry-date\" datetime=\"2014-01-01\"></time></p>"
                                   {:proposed-backcompat? true}))))))

(deftest the-include-pattern
  (let [names #(map (comp :name :properties) (:items (mf/parse % url)))]
    (is (= [["Jane"]]
           (names "<div id=\"x\"><p class=\"vcard\"><b class=\"fn\">Jane</b><a class=\"include\" href=\"#x\"></a></p></div>")))
    (is (= [["Jane"] ["Joe"]]
           (names "<p class=\"vcard\" id=\"a\" itemref=\"b\"><b class=\"fn\">Jane</b></p><p class=\"vcard\" id=\"b\" itemref=\"a\"><b class=\"fn\">Joe</b></p>")))))

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
                 [:items 0 :properties :url])))
  (is (= ["https://jane.example/posts/jane"]
         (get-in (mf/parse (str "<base href=\"javascript:alert(1)\">"
                                "<a class=\"h-card\" href=\"jane\">Jane</a>")
                           url)
                 [:items 0 :properties :url]))
      "never a javascript: base, as in HTML"))
