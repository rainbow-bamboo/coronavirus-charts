(ns coronavirus-charts.data
  (:require
   [tick.alpha.api :as t]
   [clj-http.client :as client]
   [clara.rules :refer :all]
   [cheshire.core :as cheshire]))

;; jsonista is a faster alternative to cheshire and already in project.clj
;; not sure how to use it yet.
(defn fetch-data
  "Base function to query a url and parse"
  [url]
  (cheshire/parse-string (:body (client/get url {:accept :json}))
                         true))


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

(def sample-locations (get-all-locations-jhu))

;; This will be handy when creating timelines later
(defn extract-location
  "Base function to extract keys from a parsed location."
  [loc]
  {:id (:id loc)
   :latest (:latest loc)})

(map extract-location sample-locations)

(defn get-all-locations
  "Queries the xapix covid-19 api to return imformation about all locations, including timelines
  [source: Johns Hopkins University]"
  []
  (let [data (fetch-data "http://covid19api.xapix.io/v2/locations?timelines=true")]
    (:locations data)))

;; (get-latest-global)
(defrecord WebRequest [url])
(defrecord ParsedRequest [path arguments])

(defrecord ChartRequest [url type])
(defrecord LocationRequest [url location])
(defrecord TimeRequest [url time])

(defrecord BarChart [url code valid-time])

(defrecord Report [source-name
                   source-url
                   country
                   country-code
                   country-population
                   province
                   last-updated
                   coordinates
                   latest])



(defrule parse-request
  "Whenever there's a WebRequest, create a corresponding ParsedRequest"
  [WebRequest (= ?url url)]
  =>
  (insert! (->ParsedRequest ?url ["Hello" 9])))

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
     (t/inst (:last_updated r))
     (:coordinates r)
     {:confirmed (:confirmed latest)
      :deaths (:deaths latest)})))

(def jhu-reports  (map create-jhu-report (get-all-locations-jhu)))

(second jhu-reports)

(defquery query-country
  [:?country-code]
  [?report <- Report (= ?country-code country-code)])

;; Rename this
(defn is-valid?
  "Given an tick/inst, returns true if that time is within a tolerance (mins) "
  [time tolerance]
  (< (t/hours (t/between time (t/inst))) tolerance))


(defn search-jhu-reports [country-code]
  (let [facts  (map create-jhu-report (get-all-locations))
        session (-> (mk-session [query-country])
                    (insert-all facts)
                    (fire-rules))]
    (query session query-country :?country-code country-code)))


;; This does not work.
;; rules are only fired if
(defrule update-jhu-reports
  [:not [Report (is-valid? last-updated 48)]]
  =>
  (println "doing an update")
  (insert-all! (map create-jhu-report (get-all-locations))))


(defquery query-parsed-request
  [:?path]
  [ParsedRequest (= ?path path)])


(def jhu-session (atom (-> (mk-session [query-country
                                        update-jhu-reports
                                        query-parsed-request])
                           (insert-all  (map create-jhu-report (get-all-locations)))
                           (insert (->ParsedRequest "/c/er" ["c" "er"]))
                           (fire-rules))))

(defn search-reports-by-country [session country-code]
  (fire-rules session)
  (:?report
   (first (query session query-country  :?country-code country-code))))


(:latest (search-reports-by-country @jhu-session "ES"))



;; We're making a time test in order to update our sources every 30 minutes
;; the goal is to develop a rule which will println if time has passed

;; (def sample-time (get-in (first (search-reports-by-country @jhu-session "US")) [:?report :last-updated]))


;; (is-old? sample-time 46)








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

(:country (:?report (first (search-jhu-reports "ES"))))
