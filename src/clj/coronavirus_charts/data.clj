(ns coronavirus-charts.data
  (:require
   [tick.alpha.api :as t]
   [clj-http.client :as client]
   [clara.rules :refer :all]
   [clara.rules.accumulators :as acc]
   [coronavirus-charts.chartwell :as cw]
   [coronavirus-charts.charts :as charts]
   [cheshire.core :as cheshire]
   [clojure.string :as string]))

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

;; (map extract-location sample-locations)

(defn get-all-locations
  "Queries the xapix covid-19 api to return imformation about all locations, including timelines
  [source: Johns Hopkins University]"
  []
  (let [data (fetch-data "http://covid19api.xapix.io/v2/locations?timelines=true")]
    (:locations data)))

;; (get-latest-global-jhu)

;; Possibly implement core.spec on records
(defrecord WebRequest [url])
(defrecord ParsedRequest [path argument])

(defrecord ChartRequest [path chart-type body])
(defrecord LocationRequest [path location-name country-code jhu-report])
(defrecord DateRequest [path date])

(defrecord RenderedPage [path html])

(defrecord BarChart [path body])

(defrecord C19Report [source-name
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
  [WebRequest
   (= ?url url)
   (= ?arguments (string/split ?url #"/"))]
  =>
  (insert-all! (map (fn [arg]
                      (if (not (string/blank? arg))
                        (ParsedRequest. ?url arg))) ?arguments)))

(defrule print-args
  [ParsedRequest (= ?arg argument)]
  =>
  (println ?arg))


(defrule create-bars
  [:and
   [LocationRequest
    (= ?path path)
    (= ?location-name location-name)
    (= ?report jhu-report)]
   [ParsedRequest
    (= ?arg argument)
    (= ?arg "bar")]]
  =>
  (insert! (ChartRequest. ?path "bar" (cw/latest-table ?report)))
  (println "in cbars"  ?arg))

(defrule create-latest-chart-by-country-code
  [LocationRequest
   (= ?path path)
   (= ?report jhu-report)]
  =>
  (insert-all! (list
                (ChartRequest. ?path "bar" (cw/latest-bar ?report))
                (ChartRequest. ?path "table" (cw/latest-table ?report))
                (ChartRequest. ?path "source" (cw/source-box ?report)))))

(defrule create-chart-page
  [?fragments <- (acc/all) :from [ChartRequest (= ?path path)]]
  =>
  (insert! (RenderedPage. ?path (charts/base-chart "Heading" (map :body ?fragments)))))


(defn create-jhu-report
  "Given the parsed json from the api call, return a C19Report record"
  [r]
  (let [latest (:latest r)]
    (C19Report.
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

(defquery query-country
  [:?country-code]
  [?report <- C19Report (= ?country-code country-code)])

(defquery query-chart-request
  [:?path]
  [ChartRequest (= ?path path) (= ?chart-type chart-type) (= ?body body)])

(defquery query-location-request
  [:?path]
  [LocationRequest (= ?path path) (= ?location-name location-name)])

(defquery query-rendered-page
  [:?path]
  [RenderedPage (= ?path path) (= ?html html)])

(defn is-within-time?
  "Given an tick/inst, returns true if that time is within a tolerance (mins) "
  [time tolerance]
  (< (t/hours (t/between time (t/inst))) tolerance))

(defn is-same-day?
  "Given two tick/inst, returns true if they are on the same day"
  [t1 t2]
  (= (t/date t1) (t/date t2)))

(defn test-date [date-string]
  (let [date (try (t/date date-string) (catch Exception e false))]
  (if date
    true
    false)))


(defn search-jhu-reports [country-code]
  (let [facts  (map create-jhu-report (get-all-locations))
        session (-> (mk-session [query-country])
                    (insert-all facts)
                    (fire-rules))]
    (query session query-country :?country-code country-code)))


;; The intention is that if there is a single C19Report that's within a tolerance
;; of 48 hours, then we can assume that the dataset has been updated within
;; 48 hours and then do nothing.
;; else, in the case that we there are no valid reports, we query xapix and
;; insert an entire list of Records.
;; I'm not sure if this implementation is correct.
(defrule update-jhu-reports
  [:not [C19Report (is-within-time? last-updated 48)]]
  =>
  (println "doing an update")
  (insert-all! (map create-jhu-report (get-all-locations))))


(defquery query-parsed-request
  []
  [ParsedRequest (= ?path path) (= ?argument argument)])

(defquery all-parsed [] [ParsedRequest (= ?path path)])
(defquery all-locations [] [LocationRequest (= ?path path) (= ?location-name location-name)])


(defrule parse-locations
  "Checks if the argument parsed matches any country-codes"
  [ParsedRequest (= ?arg argument) (= ?path path)]
  [?report <- C19Report (= ?country-code country-code)]
  [:test (= ?country-code (string/upper-case ?arg))]
  =>
  (insert! (LocationRequest. ?path (:country ?report) (:country-code ?report) ?report)))

(defrule parse-dates
  "Checks if the argument parsed matches a date int he form YYYY-MM-DD"
  [ParsedRequest (= ?arg argument) (= ?path path)]
  [:test (test-date ?arg)]
  =>
  (println "parsing dates")
  (insert! (DateRequest. ?path (t/date ?arg))))


(def jhu-session (atom (-> (mk-session [parse-request
                                        query-country
                                        ;;                                        update-jhu-reports
                                        create-latest-chart-by-country-code
                                        create-chart-page
                                        all-locations
                                        parse-locations
                                        query-parsed-request
                                        query-chart-request
                                        query-location-request
                                        query-rendered-page
                                        parse-dates
                                        ])
                           (insert-all (map create-jhu-report (get-all-locations)))
                           (fire-rules))))

(query @jhu-session query-location-request)


;; Look how we dereference the atom to get access to the current state of the session
;; then we insert a new fact into that state (call it new fact)
;; and then we fire the rules
(-> @jhu-session
    (insert (->WebRequest "/hello/world"))
    (fire-rules)
    (query query-parsed-request))

;; ({:?path "/hello/world", :?argument "world"}
;;  {:?path "/hello/world", :?argument "hello"}
;;  {:?path "/c/e/12/n", :?argument "c"}
;;  {:?path "/c/e/12/n", :?argument "e"}
;;  {:?path "/c/e/12/n", :?argument "12"}
;;  {:?path "/c/e/12/n", :?argument "n"})


(defn insert-web-request [url]
  (-> @jhu-session
      (insert (->WebRequest url))
      (fire-rules)
      (query query-rendered-page :?path url)
      ))

;; (:?html (first (insert-web-request "/bar/tt/es/us/2020-03-28")))


;; the function to get charts will just insert a new request
;; let it generate everything it needs to gen
;; and then reset

(defn search-reports-by-country [session country-code]
  (fire-rules session)
  (:?report
   (first (query session query-country  "?country-code" country-code))))


;; (cw/latest-bar (search-reports-by-country @jhu-session "ES"))



;; (first (:locations ))
;; {:id 0,
;;  :country "Afghanistan",
;;  :country_code "AF",
;;  :country_population 29121286,
;;  :province "",
;;  :last_updated "2020-03-29T13:14:06.151058Z",
;;  :coordinates {:latitude "33.0", :longitude "65.0"},
;;  :latest {:confirmed 110, :deaths 4, :recovered 0}}
