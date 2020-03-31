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
    (layout/render request "home.html")))


(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/*" {:get (fn [resp]
                 (-> (response/ok (render-web-request (:path-info resp)))
                     (response/header "content-type" "text/html")))}]])


;; (-> (mk-session)
;;     (insert (->WebRequest "hh.to/tt"))
;;     (fire-rules)
;;     (get-parsed-requests))
