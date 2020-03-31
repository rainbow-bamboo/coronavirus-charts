(ns coronavirus-charts.chartwell
  (:require [clojure.math.numeric-tower :as math]))

;; FF-Chartwell is a webfont that creates simple charts out of integers.
;;
;; For example, this span:
;; <span class="vertical-bars">10+50+100</span>
;;
;; with this css:
;; .vertical-bars{
;;   font-family: "FFChartwellBarsVerticalRegular";
;;   font-variant-ligatures: discretionary-ligatures;
;;   }
;;
;; will return a bar-graph with 3 bars:
;; 1: 10% of font-height
;; 2: 50%
;; 3: 100%

;; It also has support for horizontal-bars, pies,
;; radars, rings, areas, roses, bubbles, scatters and lines...
;; as well as a couple "floating" variants.

;; The font technology used (discretionary liglatures) is
;; supported in everything except IE, and I assume that most of
;; the bugs in FF Chartwell have been ironed out because it's
;; been active since 2012.

;; Here is a sample reagent component
;; (defn simple-component []
;;   [:div
;;     [:span {:style {:color "blue"}} "I am blue text"]
;;     [:span {:style {:color "red"}} "I am red text"]]])
;;
;; I'm imagining that the api will look like:
;; (<chart-type> <values> <default-colors> <class-name>)
;;
;; With the function for vertical-bars resembling:
;; (vertical-bars [10 20] ["#bee" "#fab] "v-bar")
;;
;;  => [:div.vertical-bar [:span "10" {:style {:color "#bee"} :class "chart-segment"}]
;;  =>                    [:span "20" {:style {:color "#fab"} :class "chart-segment"}]]
;;
;; We can abstract the "span" structure into a `chart-segment` component
;; since it'll represent the smallest building block of a chart.

;; Since each chart segment is a part of a whole chart,
;; react/reagent requires us to provide a unique key for the virtual dom

(defn chart-segment
  "Given the integer size, a hexcode color, and a class string,
   return a reagent span component."
  [content color class]
;;  ^{:key (random-uuid)} Don't think that this is necessary in clj
  [:span {:style (str "color:" color)
          :class (str "chart-segment " class)}
   (reduce str content)])

;; It's meant to work with the (herb)[http://herb.roosta.sh/] library for more complex
;; styling like: (chart-segment 10 "#bee" (<class sample-class-func))

;; Note how the key prop is stored as meta
;; (meta (chart-segment "lol" "red" (sample-id-func "101010")))
;; => {:key "1010101584214541804"}

;; Our dream is to return
;; [:div.vertical-bar [chart-segment _ _ _ ] [chart-segment _ _ _ ] ]

;; We're handing the anonymous fn, [size color] pairs
;; and then we're destructuring it through the [[x y]] syntax
;; in order to generate list of `chart-segment`

;; I'm sure that there's a more elegant way of doing this in one
;; function.
;; Note that the `class-func` must be a function that accepts [size color]
;; [ Is this something we can express in clojure.spec? ]
(defn herb-vertical-bars [sizes colors grid class-func]
  [:div.vertical-bars
   (cons [:span {:class "vertical-bars-grid"} (str grid "+")]
         (map
          (fn [[size color]] (chart-segment (str size "+")
                                               color
                                               (class-func size color)))
          (map vector sizes colors)))])

;; (def sample-sizes [10 50 100])
;; (def sample-colors ["#bee" "#fab" "#ada"])

;; This is effectively a convert to percentage function
;; except that it rounds up
(defn v-bar-scale [v target]
  (int (math/ceil (* 100 (/ v target)))))

;; the target scale is calculated through finding the largest value
;; in the dataset. I'd like to make it round up to the nearest hundred.
(defn target-scale [dataset]
  (apply max dataset))


(defn vertical-bars [sizes colors grid class]
  (let [target (target-scale sizes)]
    [:div.vertical-bars
     (cons
      [:span {:class "vertical-bars-grid"} (str grid "+")]
      (map
       (fn [[size color]] (chart-segment (str (v-bar-scale size target) "+")
                                            color
                                            class))
       (map vector sizes colors)))]))


(defn source-box [r]
  (let [source-name (:source-name r)
        source-url (:source-url r)]
    [:p.sources "Source: "
     [:a {:href source-url} source-name]
     " via "
     [:a {:href "https://xapix-io.github.io/covid-data/"} "xapix" ]]))

(defn latest-bar [r]
  (let [latest (:latest r)
        c (:confirmed latest)
        d (:deaths latest)]
    [:div.bar-chart
     [:h2 (:country r)]
     (vertical-bars [c d] ["#dab101" "#110809"] "d" "latest-bar")]))

(defn latest-table
  "Given a C19Report, create a table from the :latest key"
  [r]
  (let [latest (:latest r)
        c (:confirmed latest)
        d (:deaths latest)]
    [:table.blocky-table
     [:thead
      [:tr
       [:th "Confirmed"]
       [:th "Deaths"]]]
     [:tbody
      [:tr
       [:td c]
       [:td d]]]]))
