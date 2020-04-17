(ns coronavirus-charts.sources.jhu
  (:require
   [coronavirus-charts.data :as d]
   [clojure.pprint :refer [pprint]]
   [clojure.walk :refer [stringify-keys]]
   [tick.alpha.api :as t]
   [where.core :refer [where]]
   [com.rpl.specter :refer :all]))

(defrecord JHUReport
    [source-name
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

(defn get-latest-global-jhu
  "Queries the xapix covid-19 api to return the latest confrimed and deaths
  I expect to update these end points when xapix settles on a spec.
  [source: Johns Hopkins University]"
  []
  (let [data (:latest (d/fetch-data "http://covid19api.xapix.io/v2/latest"))]
    {:confirmed (:confirmed data) :deaths (:deaths data)}))


(defn get-all-locations-jhu
  "Queries the xapix covid-19 api to return imformation about all locations
  [source: Johns Hopkins University]"
  []
  (let [data (d/fetch-data "http://covid19api.xapix.io/v2/locations")]
    (:locations data)))

(defn get-all-timelines-jhu
  "Queries the xapix covid-19 api to return imformation about all locations, including timelines
  [source: Johns Hopkins University]"
  []
  (let [data (d/fetch-data "http://covid19api.xapix.io/v2/locations?timelines=true")]
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
   :last-updated (t/inst (:last_updated report))
   :province (:province report)
   :country (:country report)})

(defn extract-dated-cases [timeline]
  (let [confirmed (extract-timeline-by-key timeline :confirmed)
        deaths (extract-timeline-by-key timeline :deaths)]
    (zipmap confirmed deaths)))


;;            Confirmed Cases                 Deaths
;; => [[#time/date "2020-03-26" 110] [#time/date "2020-03-26" 4]]


(defn date-readings [report]
  "Given the a parsed jhu-report, return list of maps
   representing the # of confirmed cases for each individual day at
   that location. ({:date X :deaths Y :confirmed Z}, ...)"
  (let [time-reports (extract-dated-cases (:timelines report))]
    (map (fn [[confirmed deaths]]
           {:date (first deaths)
            :deaths (second deaths)
            :confirmed (second confirmed)}) time-reports)))

;; (pprint (date-readings  (first jhu-timelines)))
;; (pprint (take 2 (map date-readings jhu-timelines)))


;; Each of these needs to be transformed into a Fact
(defn compose-location-time-reports [report]
  (let [location-data (extract-timeline-metadata report)
        case-data (date-readings report)]
    (map (fn [c]
           (merge c location-data))
         case-data)))

;; (pprint (take 2 (compose-location-time-reports (first jhu-timelines))))
;; ({:last-updated #inst "2020-04-10T13:49:21.642-00:00",
;;   :date #time/date "2020-03-25",
;;   :coordinates {:latitude "33.0", :longitude "65.0"},
;;   :deaths 94,
;;   :country-code "AF",
;;   :confirmed 4,
;;   :country-population 29121286,
;;   :latest {:confirmed 484, :deaths 15},
;;   :province "",
;;   :country "Afghanistan"}
;;  {:last-updated #inst "2020-04-10T13:49:21.642-00:00",
;;   :date #time/date "2020-03-26",
;;   :coordinates {:latitude "33.0", :longitude "65.0"},
;;   :deaths 110,
;;   :country-code "AF",
;;   :confirmed 4,
;;   :country-population 29121286,
;;   :latest {:confirmed 484, :deaths 15},
;;   :province "",
;;   :country "Afghanistan"})

(def location-time-reports
  (map compose-location-time-reports (get-all-timelines-jhu)))

(first location-time-reports)


(defn create-location-report
  "Given a location-time-report map, return a LocationReport record"
  [r]
  (let [{:keys [country
           country-code
           country-population
           province
           last-updated
           coordinates
           date
           deaths
           confirmed
           latest]}
        r]
    (JHUReport.
     "Johns Hopkins University"
     "https://github.com/CSSEGISandExternal-Data/COVID-19"
     country
     country-code
     country-population
     province
     last-updated
     coordinates
     date
     deaths
     confirmed
     latest)))



(defn jhu-report-records
  "This function queries the xapix jhu endpoint and then transforms the timelines
  there into a collection of JHUReport records."
  []
  (let [timelines (get-all-timelines-jhu)
        location-time-reports (map compose-location-time-reports timelines)]
    (select [ALL ALL]
            (transform [ALL ALL]
                       create-location-report location-time-reports))))


;; Queries

(defquery query-jhu-reports
  [:?country]
  [?jhu-report <- jhu/JHUReport (= ?country country)])

(defn query-country-jhu
  "Given a location name, eg 'Italy,' we query a defreferenced
  wikipedia-session and return the appropriate JHUReport record."
  [session country]
  (-> @session
      (query query-jhu-reports :?country country)))

;; End Queries

;; => 22440

;; SPECTER EXPERIMENTS ;;
;; (def jhu-timelines (get-all-timelines-jhu))

;; (def stringified-timelines
;;   "When we first parse the json, we end up storing time strings as
;;   keywords. We first go in and turn them to strings."
;;   (transform [ALL :timelines MAP-VALS :timeline]
;;              stringify-keys
;;              jhu-timelines))

;;  (def parsed-timelines
;;    "Next, read them as tick dates."
;;    (transform [ALL :timelines MAP-VALS :timeline MAP-KEYS]
;;                                   #(t/date (t/inst %))
;;                                   stringified-timelines))

;; (def pruned-timelines
;;   "Finally, we remove the extra :recovered key which is no longer reported by jhu"
;;   (setval [ALL :timelines :recovered] NONE parsed-timelines))


;; Now that we have our timelines in a more consistent form,
;; we can begin constructing our Facts. First let's try to extract the
;; report metadata... basically anything that's not the core timelines.

;; (defn extract-timeline-metadata [report]
;;   {:coordinates (:coordinates report)
;;    :country-code (:country_code report)
;;    :latest {:confirmed (:confirmed (:latest report))
;;             :deaths (:deaths (:latest report))}
;;    :country-population (:country_population report)
;;    :last-updated (t/inst (:last_updated report))
;;    :province (:province report)
;;    :country (:country report)})


;; (defn extract-dated-cases [report]
;;   (let [timelines (:timelines report)
;;         confirmed (select [:confirmed :timeline] timelines)
;;         deaths (select [:deaths :timeline] timelines)
;;         dates (select [ALL MAP-KEYS]
;;                       confirmed)]
;;     dates))

;; ;; (take 6 (select [ALL :timelines MAP-KEYS] pruned-timelines))
;; ;; => (:confirmed :deaths :confirmed :deaths :confirmed :deaths)


;; ;; This is going in and getting a reading on a date.
;; (get
;;  (first (select [ALL :timelines MAP-VALS :timeline] pruned-timelines))
;;   (t/date "2020-03-21"))


;; ;; This gets the latest based on a country code
;; (select [ALL (where :country_code :IS? "us") :latest] pruned-timelines)
