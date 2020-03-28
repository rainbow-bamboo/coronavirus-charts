(ns coronavirus-charts.routes.home
  (:require
   [coronavirus-charts.layout :as layout]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [coronavirus-charts.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]
   [clara.rules :refer :all]))

(def chart-session (mk-session))

(defrecord WebRequest [url])
(defrecord ParsedRequest [url arguments])

(defrecord ChartRequest [url type])
(defrecord LocationRequest [url location])
(defrecord TimeRequest [url time])

(defrecord BarChart [url code valid-time])

(defrule parse-request
  "Whenever there's a WebRequest, create a corresponding ParsedRequest"
  [WebRequest (= ?url url)]
  =>
  (insert! (->ParsedRequest ?url ["Hello" 9])))

(defquery get-parsed-request
  [:?url]
  [?request <- ParsedRequest (= ?url url)])


(defn home-page [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (println  (string/split (:uri request) #"/"))
  (layout/render request "about.html"))

(defn chart-page [request]
  (let [params  (string/split (:uri request) #"/")]
    (insert chart-session (->ParsedRequest params))
    (layout/render request "about.html")))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/*" {:get chart-page}]
   ["/about" {:get about-page}]])


(-> (mk-session)
    (insert (->WebRequest "hh.to/tt"))
    (fire-rules)
    (get-parsed-request "hh.to/tt"))
