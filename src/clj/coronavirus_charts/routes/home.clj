(ns coronavirus-charts.routes.home
  (:require
   [coronavirus-charts.layout :as layout]
   [clojure.java.io :as io]
   [coronavirus-charts.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]
   [ring.middleware.params :as params]
   [clara.rules :refer :all]))

(defn home-page [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (println (:query-params request))
  (layout/render request "about.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 params/wrap-params
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/t/*" {:get about-page}]
   ["/about" {:get about-page}]])


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

(-> (mk-session)
    (insert (->WebRequest "hh.to/tt"))
    (fire-rules)
    (get-parsed-request "hh.to/tt"))
