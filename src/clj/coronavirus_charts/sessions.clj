(ns coronavirus-charts.sessions
  (:require
   [tick.alpha.api :as t]
   [clojure.pprint :refer [pprint]]
   [clj-http.client :as client]
   [clara.rules :refer :all]
   [clara.rules.accumulators :as acc]
   [coronavirus-charts.chartwell :as cw]
   [coronavirus-charts.charts :as charts]
   [coronavirus-charts.data :as external-data]
   [cheshire.core :as cheshire]
   [clojure.string :as string]))




;; Helpers
(defn is-within-time?
  "Given an tick/inst, returns true if that time is within a tolerance (mins) "
  [time tolerance]
  (< (t/hours (t/between time (t/inst))) tolerance))

(defn is-same-day?
  "Given two tick/inst, returns true if they are on the same day"
  [t1 t2]
  (= (t/date t1) (t/date t2)))

(defn is-date? [date-string]
  (let [date (try (t/date date-string) (catch Exception e false))]
  (if date
    true
    false)))


;; This is an important function that needs work.
;; The intention is that it reads the path, and then splits it
;; into each discrete parameter to be inserted as individual facts
;; in the session.
;; Commas are valid seperators because it takes two actions to get to a
;; slash when you're typing in Whatsapp, but commas
;; are usually one press away. `📊📊.to/cn,us,es,tt` would be a valid url
(defn parse-path
  "Given a url path, eg, /tt/us or /bar/en,us,tt/2020-03-28
   return a flattened list of all arguments eg. ['tt' 'us']"
  [path]
  (flatten
   (map (fn [v] (string/split v #","))
        (string/split path #"/"))))

;; END Helpers

;; FactTypes
;; TODO: Implement core.spec or schema on records.
(defrecord WebRequest [url])
(defrecord ParsedRequest [path argument])

(defrecord LocationRequest [path
                            location-name
                            country-code
                            location-report])

(defrecord DateRequest [path date])

(defrecord NavFragment [path nav-type body])
(defrecord ChartFragment [path chart-type body])
(defrecord RenderedPage [path html])


(defrecord GlobalReport [source-name
                         source-url
                         last-queried
                         latest])

(defrecord LocationReport [source-name
                           source-url
                           country
                           country-code
                           country-population
                           province
                           last-updated
                           coordinates
                           timelines
                           latest])

;; END FactTypes


;; Report Manipulation

(defn create-location-report
  "Given the parsed json from the api call, return a LocationReport record"
  [r]
  (let [latest (:latest r)]
    (LocationReport.
     "Johns Hopkins University"
     "https://github.com/CSSEGISandExternal-Data/COVID-19"
     (:country r)
     (:country_code r)
     (:country_population r)
     (:province r)
     (t/inst (:last_updated r))
     (:coordinates r)
     (:timelines r)
     {:confirmed (:confirmed latest)
      :deaths (:deaths latest)})))


;; (def location-reports  (map create-location-report (external-data/get-all-locations-jhu)))

(defn create-jhu-global-report
  ""
  [stats]
  (GlobalReport.
   "Johns Hopkins University"
   "https://github.com/CSSEGISandExternal-Data/COVID-19"
   (t/inst)
   stats))



(defn create-nav [nav-data]
  (let [locations (get nav-data "Location")
        dates  (get nav-data "Date")]
    (list
     (charts/make-nav (map :body locations) "locations" "Locations")
     (charts/make-nav (map :body dates) "dates" "Dates"))))

;; END Report Manipulation



;; RULES

;; This only has to check for blanks because my parse function is bad.
(defrule parse-request
  "Whenever there's a WebRequest, create a corresponding ParsedRequest"
  [WebRequest
   (= ?url url)
   (= ?arguments (parse-path ?url))]
  =>
  (insert-all! (map (fn [arg]
                      (if (not (string/blank? arg))
                        (ParsedRequest. ?url arg))) ?arguments)))


(defrule print-args
  [ParsedRequest (= ?arg argument)]
  =>
  (println ?arg))


(defrule create-latest-chart-by-country-code
  [LocationRequest
   (= ?path path)
   (= ?report location-report)]
  =>
  (insert-all! (list
                (ChartFragment. ?path "bar" (cw/latest-bar ?report))
                (ChartFragment. ?path "table" (cw/latest-table ?report))
                (ChartFragment. ?path "source" (cw/source-box ?report)))))

(defrule create-home-page-charts
  [WebRequest
   (= ?url url)
   (or
    (= "/" ?url)
    (string/blank? ?url))]
  [?global-report <- (acc/max :last-queried :returns-fact true) :from [GlobalReport (= ?latest latest)]]
  =>
  (insert-all! (list
                (ChartFragment. ?url "global" (cw/global-bar ?global-report))
                (ChartFragment. ?url "table" (cw/latest-table ?global-report)))))



(defrule create-chart-page
  [?chart-fragments <- (acc/all) :from [ChartFragment (= ?path path)]]
  [?nav-fragments <- (acc/grouping-by :nav-type) :from [NavFragment (= ?path path)]]
  =>
  (insert! (RenderedPage. ?path
                          (charts/base-chart "Recorded Coronavirus (COVID-19) Cases"
                                             (create-nav ?nav-fragments)
                                             (map :body ?chart-fragments)))))


;; The intention is that if there is a single LocationReport that's within a tolerance
;; of 48 hours, then we can assume that the dataset has been updated within
;; 48 hours and then do nothing.
;; else, in the case that we there are no valid reports, we query xapix and
;; insert an entire list of records.
;; I'm not sure if this implementation is correct.
(defrule update-location-reports
  [:not [LocationReport (is-within-time? last-updated 48)]]
  =>
  (println "doing an update")
  (insert-all! (map create-location-report (external-data/get-all-timelines-jhu))))



(defrule parse-locations
  "Checks if the argument parsed matches any country-codes"
  [?parsed <- ParsedRequest (= ?arg argument) (= ?path path)]
  [?report <- LocationReport (= ?country-code country-code) (= ?country country)]
  [:test (or
          (= ?country-code (string/upper-case ?arg))
          (= (string/lower-case ?country) (string/lower-case ?arg)))]
  =>
  (insert! (LocationRequest. ?path (:country ?report) (:country-code ?report) ?report))
  (pprint (:timelines ?report))
  (insert! (NavFragment. ?path "Location" (:country ?report))))
;; How do we account for States?

(defrule parse-dates
  "Checks if the argument parsed matches a date int he form YYYY-MM-DD"
  [ParsedRequest (= ?arg argument) (= ?path path)]
  [:test (is-date? ?arg)]
  =>
  (println "parsing dates")
  (insert! (DateRequest. ?path (t/date ?arg)))
  (insert! (NavFragment. ?path "Date" (t/date ?arg))))



;; END RULES

;; Queries
(defquery query-country
  [:?country-code]
  [?report <- LocationReport (= ?country-code country-code)])

(defquery query-chart-request
  [:?path]
  [ChartFragment (= ?path path) (= ?chart-type chart-type) (= ?body body)])

(defquery query-location-request
  [:?path]
  [LocationRequest (= ?path path) (= ?location-name location-name)])

;; Right now there's only ever one WebRequest in a session, and therefore
;; only one RenderedPage, but it's easy to imagine caching where we keep
;; every RenderedPage, and only fire rules if the most recent RenderedPage fact
;; is beyond some time/freshness tolerance.
(defquery query-rendered-page
  [:?path]
  [RenderedPage (= ?path path) (= ?html html)])

(defquery query-parsed-request
  [:?path]
  [ParsedRequest (= ?path path) (= ?argument argument)])

(defquery all-parsed [] [ParsedRequest (= ?path path)])

(defquery all-locations [] [LocationRequest (= ?path path) (= ?location-name location-name)])


;; END QUERIES


;; Exploratory Functions

(defn search-location-reports [country-code]
  (let [facts  (map create-location-report (external-data/get-all-timelines-jhu))
        session (-> (mk-session [query-country])
                    (insert-all facts)
                    (fire-rules))]
    (query session query-country :?country-code country-code)))


(def jhu-session (atom (-> (mk-session [parse-request
                                        query-country
                                        update-location-reports
                                        create-home-page-charts
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
                           (insert-all (map create-location-report (external-data/get-all-timelines-jhu)))
                           (insert (create-jhu-global-report (external-data/get-latest-global-jhu)))
                           (fire-rules))))

;; Look how we dereference the atom to get access to the current state of the session
;; then we insert a new fact into that state
;; and then we fire the rules
;; (-> @jhu-session
;;     (insert (->WebRequest "/hello/world"))
;;     (fire-rules)
;;     (query query-parsed-request))


;; I'm not sure if 'render' is the right name
;; will revisit soon. The intention is that a web request comes in
(defn render-web-request
  "Given a url path, eg. '/us/2020-03-28/tt' insert a new WebReqest fact into a rules
   engine session, fire all the rules, and then query for any RenderedPage facts.
   Finally, we return the raw html content of that fact"
  [path]
  (-> @jhu-session
      (insert (->WebRequest path))
      (fire-rules)
      (query query-rendered-page :?path path)
      (first)
      (:?html)))

;; (render-web-request "tt/us")


(defn search-reports-by-country [session country-code]
  (fire-rules session)
  (:?report
   (first (query session query-country  "?country-code" country-code))))


;; (cw/latest-bar (search-reports-by-country @jhu-session "ES"))





;; END Exploratory


;; Example location result (without timelines)
;; {:id 0,
;;  :country "Afghanistan",
;;  :country_code "AF",
;;  :country_population 29121286,
;;  :province "",
;;  :last_updated "2020-03-29T13:14:06.151058Z",
;;  :coordinates {:latitude "33.0", :longitude "65.0"},
;;  :latest {:confirmed 110, :deaths 4, :recovered 0}}
