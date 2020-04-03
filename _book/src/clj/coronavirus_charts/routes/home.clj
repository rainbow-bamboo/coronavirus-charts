(ns coronavirus-charts.routes.home
  (:require
   [coronavirus-charts.layout :as layout]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [coronavirus-charts.middleware :as middleware]
   [coronavirus-charts.data :refer :all]
   [ring.util.response]
   [ring.util.http-response :as response]
   [clara.rules :refer :all]))

(defn home-page [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (println  (string/split (:path-info request) #"/"))
  (layout/render request "about.html"))

(defn chart-page [request]
  (let [path (:path-info request)
        params  (string/split path #"/")]
    (println params)
    (reset! jhu-session (insert @jhu-session (->ParsedRequest "/c/er" ["c" "er"])))
    (println  "Hello" (query @jhu-session query-parsed-request :?path "/c/er"))
    (layout/render request "home.html")))


(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/*" {:get chart-page}]])


;; (-> (mk-session)
;;     (insert (->WebRequest "hh.to/tt"))
;;     (fire-rules)
;;     (get-parsed-requests))
