(ns coronavirus-charts.oz
  (:require [oz.core :as oz]))

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
(oz/view! line-plot)

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
(oz/export! viz "export-test.html")

(oz/build! line-plot)
