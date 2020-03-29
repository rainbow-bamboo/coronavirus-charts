(ns coronavirus-charts.data
  (:require
   [tick.alpha.api :as t]
   [clj-http.client :as client]
   [clara.rules :refer :all]
   [cheshire.core :as cheshire]))

(defn fetch-data [url]
  (cheshire/parse-string (:body (client/get url {:accept :json}))
                         true))

(defn get-latest-global []
  (let [data (:latest (fetch-data "http://covid19api.xapix.io/v2/latest"))]
    {:confirmed (:confirmed data) :deaths (:deaths data)}))

(defn get-all-locations []
  (let [data (fetch-data "http://covid19api.xapix.io/v2/locations?timelines=true")]
    (:locations data)))

;; (get-latest-global)

(defrecord Report [ source-name
                   source-url
                   country
                   country-code
                   country-population
                   province
                   last-updated
                   coordinates
                   timelines
                   latest])


(defn create-jhu-report
  "Given the parsed json from the api call, return a Report record"
  [r]
  (let [latest (:latest r)]
    (Report.
     "Johns Hopkins University"
     "https://github.com/CSSEGISandData/COVID-19"
     (:country r)
     (:country_code r)
     (:country_population r)
     (:province r)
     (:last_updated r)
     (:coordinates r)
     (:timelines r)
     {:confirmed (:confirmed latest)
      :deaths (:deaths latest)})))

(def af-report (create-jhu-report (first (get-all-locations))))
(:country af-report)

(get-in af-report [:timelines :confirmed :latest])


(def jhu-reports (map create-jhu-report (get-all-locations)))

(defquery query-country
  [:?country-code]
  [?report <- Report (= ?country-code country-code)])

(defn search-jhu-reports [facts country-code]
  (let [session (-> (mk-session [query-country])
                    (insert-all facts)
                    (fire-rules))]
    (query session query-country :?country-code country-code)))

(:country (:?report (first (search-jhu-reports jhu-reports "TT"))))





;; {:id 0,
;;  :country "Afghanistan",
;;  :country_code "AF",
;;  :country_population 29121286,
;;  :province "",
;;  :last_updated "2020-03-29T13:14:06.151058Z",
;;  :coordinates {:latitude "33.0", :longitude "65.0"},
;;  :latest {:confirmed 110, :deaths 4, :recovered 0}}


;; (defrecord DataSet [sourcename sourceurl title valid-time data])
;; (defrule init-global-data
;;   [:not [DataSet (= title "latest-global-jhu")]]
;;   =>
;;   (insert!  (->DataSet "Johns Hopkins University"
;;                       "https://github.com/CSSEGISandData/COVID-19"
;;                       "latest-global-jhu"
;;                       "Data data bb"
;;                       (t/now))))


;; (defrule init-locations-data
;;   [:not [DataSet (= title "locations-jhu")]]
;;   =>
;;   (insert! (->DataSet "Johns Hopkins University"
;;                       "https://github.com/CSSEGISandData/COVID-19"
;;                       :locations-jhu
;;                       "Data")))


;; (first (:locations ))
;; {:id 0,
;;  :country "Afghanistan",
;;  :country_code "AF",
;;  :country_population 29121286,
;;  :province "",
;;  :last_updated "2020-03-29T13:14:06.151058Z",
;;  :coordinates {:latitude "33.0", :longitude "65.0"},
;;  :latest {:confirmed 110, :deaths 4, :recovered 0}}
