(ns coronavirus-charts.sources.wikipedia
  (:gen-class) ;; is this necessary for the Java interop?
  (:require [net.cgrand.enlive-html :as html]
            [where.core :refer [where]]
            [com.rpl.specter :refer :all]))

(def wikipedia-coronavirus-search-url "https://en.wikipedia.org/wiki/Special:AllPages?from=Coronavirus+in&to=&namespace=0")

(defn website-content
  "Get website content from a given URL
  Arguments: web address as a string
  Returns: list of html content as hash-maps"

  [website-url]

  (html/html-resource (java.net.URL. website-url)))

(def wikipedia-index-page (website-content wikipedia-coronavirus-search-url))

;; Here we do a specter select on the enlive parsed wikipedia links to get a shortlist of links
;; filtering out all the near matches that will also pop-up such as 'Coronel, Stephen' or
;; 'Coronavirus infections' (note the space after 'in')
;; I believe that it's possible to do the entire extraction in enlive, but as soon as
;; we can get it into a recognizable map form, I just naturally reach for specter.
(def filtered-coronavirus-links (select [ALL :content ALL :attrs (where :title :CONTAINS? "Coronavirus in ")]
                                        (html/select wikipedia-index-page [:li.allpagesredirect])))

(first filtered-coronavirus-links)
;; => {:href "/wiki/Coronavirus_in_Andorra", :class "mw-redirect", :title "Coronavirus in Andorra"}






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
