(ns coronavirus-charts.charts
    (:require [cheshire.core :as cheshire]
            [hiccup.core :refer [h]]
            [coronavirus-charts.chartwell :as cw])
    (:use [hiccup.page :only (html5 include-css include-js)]))

;; PARTIALS

;; This is required for our reel layout to prevent odd sizing of components
(defn reel-script []
  [:script "(function() {
  const className = 'chart-boards';
  const reels = Array.from(document.querySelectorAll(`.${className}`));
  const toggleOverflowClass = elem => {
    elem.classList.toggle('overflowing', elem.scrollWidth > elem.clientWidth);
  };
  for (let reel of reels) {
    if ('ResizeObserver' in window) {
      new ResizeObserver(entries => {
        toggleOverflowClass(entries[0].target);
      }).observe(reel);
    }
    if ('MutationObserver' in window) {
      new MutationObserver(entries => {
        toggleOverflowClass(entries[0].target);
      }).observe(reel, {childList: true});
    }
  }
})();"])


;; CHARTS
;; These functions all take a Report record, and then return a full html page

(defn base-chart [heading [& content]]
  (html5 [:head
          [:title "coronavirus-charts.org"]
          (include-css "/css/screen.css")
          [:body
           [:h1 heading]
           [:div.center
            content]
           (reel-script)]]))
