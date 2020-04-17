(ns coronavirus-charts.sources.wikipedia
  (:gen-class) ;; is this necessary for the Java interop?
  (:require [coronavirus-charts.sources.jhu :as jhu]
            [net.cgrand.enlive-html :as html]
            [where.core :refer [where]]
            [tick.alpha.api :as t]
            [clojure.pprint :refer [pprint]]
            [clara.rules :refer :all]
            [clara.rules.accumulators :as acc]
            [clojure.string :as string]
            [com.rpl.specter :refer :all]))

(def wikipedia-coronavirus-search-url "https://en.wikipedia.org/wiki/Special:AllPages?from=Coronavirus+in&to=&namespace=0")
;; This does not cover articles in the form https://en.wikipedia.org/wiki/2020_coronavirus_pandemic_in_Washington,_D.C.
;; But they seem to redirect to the same place eg. https://en.wikipedia.org/wiki/Coronavirus_in_Italy
;; ends up at https://en.wikipedia.org/wiki/2020_coronavirus_pandemic_in_Italy

(defn website-content
  "Get website content from a given URL
  Arguments: web address as a string
  Returns: list of html content as hash-maps"
  [website-url]
  (html/html-resource (java.net.URL. website-url)))

;; (def wikipedia-index-page (website-content wikipedia-coronavirus-search-url))

;; Here we do a specter select on the enlive parsed wikipedia links to get a shortlist of links
;; filtering out all the near matches that will also pop-up such as 'Coronel, Stephen' or
;; 'Coronavirus infections' (note the space after 'in')
;; I believe that it's possible to do the entire extraction in enlive, but as soon as
;; we can get it into a recognizable map form, I just naturally reach for specter.

;; (def filtered-coronavirus-links (select [ALL :content ALL :attrs (where :title :CONTAINS? "Coronavirus in ")]
;;                                         (html/select wikipedia-index-page [:li.allpagesredirect])))

(def NUM-OF-CHARS-TO-SKIP (count "Coronavirus in "))

(defn annotate-coronavirus-links [links skip-amount]
  (map (fn [l] (merge l
                      {:location (subs (:title l) skip-amount)}))
       links))

;; => {:href "/wiki/Coronavirus_in_Andorra", :class "mw-redirect", :title "Coronavirus in Andorra", :location "Andorra"}

(defrecord WikiLink
    [path
     location])

;; Defining JHUReport stuff here because importing records is proving
;; difficult

(defn create-wikilink
  "Given a link to a wikipedia page on the coronavirus,
  return a WikiLink record"
  [l]
  (let [{:keys [href location]} l]
    (WikiLink. href location)))

(defn scrape-wikiurls
  "Given an index-url, pointing to the wikipedia search page with an appropriate query,
   scrape all the relavent links, and then a list of WikiLinks"
  [index-url skip-number]
  (let [wikipedia-index-page (website-content index-url)
        filtered-coronavirus-links
        (select [ALL :content ALL :attrs (where :title :CONTAINS? "Coronavirus in ")]
                (html/select wikipedia-index-page [:li.allpagesredirect]))
        annotated-coronavirus-links
        (annotate-coronavirus-links filtered-coronavirus-links NUM-OF-CHARS-TO-SKIP)]
    (map create-wikilink annotated-coronavirus-links)))


(defn wikilink-records
  "This will scrape the wikipedia seach page for 'Coronavirus in ', and
   a return a list of WikiLink Records."
  []
  (scrape-wikiurls wikipedia-coronavirus-search-url NUM-OF-CHARS-TO-SKIP))


;; Queries
(defquery query-wikilinks
  [:?location]
  [?wikilink <- WikiLink (= ?location location)])

;; End Queries

;; Rules


;; End Rules


;; Here is a scrape of wikipedia links
(def wikilinks (wikilink-records))

;; And a scrape of jhu records
(def jhu-records (jhu-report-records2))
(first jhu-records)

;; Now we create a session, populate it, and put it in an atom.
;; I think that we can update this by periodically swapping out the
;; atom for a version with a new dataset. JHU is often change the
;; struture of the data, eg. remove the :recovered field.
;; Resetting at the level of an atom might be good.
(def wikipedia-session (atom (-> (mk-session [query-wikilinks
                                             jhu/query-jhu-reports])
                                 (insert-all wikilinks)
                                 (insert-all jhu-records)
                                 (fire-rules))))

(defn query-location-wikipedia
  "Given a location name, eg 'Italy,' we query a defreferenced
  wikipedia-session and return the appropriate WikiLink record."
  [location]
  (-> @wikipedia-session
      (query query-wikilinks :?location location)))



;; (query-location-wikipedia "Italy")
;; => ({:?wikilink #coronavirus_charts.sources.wikipedia.WikiLink{:path "/wiki/Coronavirus_in_Italy", :location "Italy"}, :?location "Italy"})

;; A sample country name from our jhu dataset
(def sample-country-name (:country (first jhu-records)))

;; Note that we defined the query and the function in the file with the JHUReports
;; Record defination. This is because I do not understand how (or if even I should)
;; import a record defination in one ns to another.
(jhu/query-country-jhu wikipedia-session sample-country-name)









;; This file will scrape the wikipedia index in order to find entries
;; that reference the coronavirus in a particular location
;; https://en.wikipedia.org/wiki/Special:AllPages?from=Coronavirus+in&to=&namespace=0

;; Then we will parse it and figure out what are the reported cases
;; for that location as stated on wikipedia

;; We then enter that into our rules engine as a WikiPage (or something),
;; after which we can compare what is published on wikipedia, to what
;; we have in our JHU dataset. If a figure reported is smaller than
;; the figure in the dataset, then we flag it as needing to be updated
;; using something like

;; RULE
;; IF [WikiURL ?url]
;; THEN [WikiScrape ?url ?body ?type(country/province) ?location ?latest]

;; RULE
;; IF [WikiScrape ?url (= ?type "country") ?location ?latest-wiki]
;;    [JHUReport (= ?country-name ?location ?latest-jhu)]
;;    (not ?latest-wiki ?latest-jhu)
;;
;; THEN [FlaggedWiki ?url ?latest-wiki ?latest-jhu]

;; RULE
;; IF [?all-flagged-wikis <- FlaggedWiki]
;; THEN [(make-fragments ?all-flagged-wikis)]


;; REFERENCES
;; https://www.youtube.com/watch?v=L9b8EGyiVXU (practicalli youtube)
;; https://github.com/practicalli/webscraper/blob/master/src/practicalli/webscraper_enlive.clj
;; [Old Clojure mediawiki library](https://github.com/stevemolitor/cloki);; (10 years old... might be worth it to either rewrite, or just export
;; to html files and then call a python script on the folder.
;; [MediaWiki API](https://www.mediawiki.org/wiki/API:Main_page)
;; [python library](https://wikipedia.readthedocs.io/en/latest/)
