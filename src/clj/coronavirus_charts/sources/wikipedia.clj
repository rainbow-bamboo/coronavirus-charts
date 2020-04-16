(ns coronavirus-charts.sources.wikipedia)

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
;; https://practicalli.github.io/blog/posts/web-scraping-with-clojure-hacking-hacker-news/
;; [Old Clojure mediawiki library](https://github.com/stevemolitor/cloki);; (10 years old... might be worth it to either rewrite, or just export
;; to html files and then call a python script on the folder.
;; [MediaWiki API](https://www.mediawiki.org/wiki/API:Main_page)
;; [python library](https://wikipedia.readthedocs.io/en/latest/)
