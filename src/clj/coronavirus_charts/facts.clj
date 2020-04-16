(ns coronavirus-charts.facts)

(defrecord JHUReport [source-name
                           source-url
                           country
                           country-code
                           country-population
                           province
                           last-updated
                           coordinates
                           date
                           deaths
                           confirmed
                      latest])
