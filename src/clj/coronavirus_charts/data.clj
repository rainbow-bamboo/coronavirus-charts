(ns coronavirus-charts.data
    (:require
     [clojure.pprint :refer [pprint]]
     [clj-http.client :as client]
     [cheshire.core :as cheshire]
     [clojure.string :as string]
     [tick.alpha.api :as t]
     [clojure.walk :refer [stringify-keys]]))

;; External Data
;; jsonista is a faster alternative to cheshire and already in project.clj
;; not sure how to use it yet.
(defn fetch-data
  "Base function to query a url and parse"
  [url]
  (cheshire/parse-string (:body (client/get url {:accept :json}))
                         true))

;; This will be handy when creating timelines later
(defn extract-location
  "Base function to extract keys from a parsed location."
  [loc]
  {:id (:id loc)
   :latest (:latest loc)})

(defn get-latest-global-jhu
  "Queries the xapix covid-19 api to return the latest confrimed and deaths
  I expect to update these end points when xapix settles on a spec.
  [source: Johns Hopkins University]"
  []
  (let [data (:latest (fetch-data "http://covid19api.xapix.io/v2/latest"))]
    {:confirmed (:confirmed data) :deaths (:deaths data)}))


(defn get-all-locations-jhu
  "Queries the xapix covid-19 api to return imformation about all locations
  [source: Johns Hopkins University]"
  []
  (let [data (fetch-data "http://covid19api.xapix.io/v2/locations")]
    (:locations data)))

(defn get-all-timelines-jhu
  "Queries the xapix covid-19 api to return imformation about all locations, including timelines
  [source: Johns Hopkins University]"
  []
  (let [data (fetch-data "http://covid19api.xapix.io/v2/locations?timelines=true")]
    (:locations data)))

;; END EXTERNAL DATA

(def jhu-timelines (get-all-timelines-jhu))

(defn extract-timeline-by-key [timeline key]
  (map (fn [[d v]]
       [(t/date (t/inst d)) v])
       (stringify-keys (get-in timeline [key :timeline]))))

(defn extract-timeline-metadata [report]
  {:coordinates (:coordinates report)
   :country-code (:country_code report)
   :latest {:confirmed (:confirmed (:latest report))
            :deaths (:deaths (:latest report))}
   :country-population (:country_population report)
   :province (:province report)
   :country (:country report)})

(defn extract-dated-cases [timeline]
  (let [confirmed (extract-timeline-by-key timeline :confirmed)
        deaths (extract-timeline-by-key timeline :deaths)]
    (zipmap confirmed deaths)))

;; (second (extract-dated-cases (:timelines (first jhu-timelines))))
;;            Confirmed Cases                 Deaths
;; => [[#time/date "2020-03-26" 110] [#time/date "2020-03-26" 4]]


(defn date-readings [report]
  "Given the a parsed jhu-report, return list of maps
   representing the # of confirmed cases for each individual day at
   that location. ({:date X :deaths Y :confirmed Z}, ...)"
  (let [time-reports (extract-dated-cases (:timelines report))]
    (map (fn [[deaths confirmed]]
           {:date (first deaths)
            :deaths (second deaths)
            :confirmed (second confirmed)}) time-reports)))

(pprint (date-readings  (first jhu-timelines)))
(pprint (take 2 (map date-readings jhu-timelines)))


;; Each of these needs to be transformed into a Fact
(defn compose-location-time-reports [report]
  (let [location-data (extract-timeline-metadata report)
        case-data (date-readings report)]
    (map (fn [c]
           (merge c location-data))
         case-data)))

(pprint (take 2 (compose-location-time-reports (first jhu-timelines))))
;; ({:date #time/date "2020-03-25",
;;   :coordinates {:latitude "33.0", :longitude "65.0"},
;;   :deaths 94,
;;   :country-code "AF",
;;   :confirmed 4,
;;   :country-population 29121286,
;;   :latest {:deaths 15, :confirmed 484},
;;   :province "",
;;   :country "Afghanistan"}
;;  {:date #time/date "2020-03-26",
;;   :coordinates {:latitude "33.0", :longitude "65.0"},
;;   :deaths 110,
;;   :country-code "AF",
;;   :confirmed 4,
;;   :country-population 29121286,
;;   :latest {:deaths 15, :confirmed 484},
;;   :province "",
;;   :country "Afghanistan"})

(def location-time-reports (map compose-location-time-reports jhu-timelines))

(first location-time-reports)

(defrecord LocationReport2 [source-name
                           source-url
                           country
                           country-code
                           country-population
                           province
                           last-updated
                           coordinates
                           date
                           deaths
                           confirmed
                           latest])

(defn create-location-report
  "Given the parsed json from the api call, return a LocationReport record"
  [{:keys [country country_code country_population province
           last_updated
           coordinates
           date
           deaths
           confirmed
           latest]} r]
  (LocationReport2.
   "Johns Hopkins University"
   "https://github.com/CSSEGISandExternal-Data/COVID-19"
   country
   country_code
   country_population
   province
   last_updated
   coordinates
   date
   deaths
   confirmed
   latest))


;; => {:date #time/date "2020-03-26", :deaths 26, :confirmed 409}



;; These vectors represent a date and a confirmed case count for a region.
;; Next is to get all deaths
;; Finally combine into all to create one record per time/date that include stats
;; and metadata
