(ns coronavirus-charts.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [coronavirus-charts.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[coronavirus-charts started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[coronavirus-charts has shut down successfully]=-"))
   :middleware wrap-dev})
