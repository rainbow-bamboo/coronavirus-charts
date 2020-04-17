(ns coronavirus-charts.oz
  (:require [oz.core :as oz]
            [clojure.pprint :refer [pprint]]
            [where.core :refer [where]]
            [coronavirus-charts.sources.jhu :as jhu]
            [com.rpl.specter :refer :all]))

(oz/start-server!)
(defn play-data [& names]
  (for [n names
        i (range 20)]
    {:time i :item n :quantity (+ (Math/pow (* i (count n)) 0.8) (rand-int (count n)))}))

(def line-plot
  {:data {:values (play-data "monkey" "slipper" "broom")}
   :encoding {:x {:field "time" :type "quantitative"}
              :y {:field "quantity" :type "quantitative"}
              :color {:field "item" :type "nominal"}}
   :mark "line"})

;; Render the plot
;; (oz/view! line-plot)

(def viz
  [:div
    [:h1 "Look Up Chicken Little"]
    [:p "A couple of small charts"]
    [:div {:style {:display "flex" :flex-direction "row"}}
     [:vega-lite line-plot]]
    [:p "A wider, more expansive chart"]
    [:h2 "If ever, oh ever a viz there was, the vizard of oz is one because, because, because..."]
   [:p "Because of the wonderful things it does"]])

;; The default oz css stylying really leans into the whole storybook thing
;; and I kinda love it.
;; (oz/export! viz "export-test.html")

(def jhu-reports (jhu/jhu-report-records))

(def time-reports
  (transform [ALL ALL :date] str jhu/location-time-reports))


;; For some reason our data, is swapping the values confirmed cases with deaths
;;
(defn p-line-plot
  "Transform data for visualization"
  [country-code]
  {:mark     "line"
   :data     {:values (select [ALL ALL (where :country-code :IS? country-code)]
                              time-reports)}
   :encoding {:x     {:field "date" :type "nominal"}
              :y     {:field "deaths" :type "quantitative"}
              :color {:field "country" :type "nominal"}}})



;; (oz/view! (p-line-plot "tt"))

(pprint (select [ALL ALL (where :country-code :IS? "tt")]
                time-reports))
