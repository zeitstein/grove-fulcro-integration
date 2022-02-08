(ns grove.app
  {:dev/always true} ;; required for register-events! macro
  (:require
   [shadow.experiments.grove.runtime :as rt]
   [shadow.experiments.grove.local :as local]
   [shadow.experiments.grove :as sg]
   [shadow.experiments.grove.events :as ev]
   [grove.ui :as ui]
   [grove.db :as db]))

(defonce rt-ref
  (rt/prepare {} db/data-ref ::rt-id))

(defn start []
  (sg/render rt-ref (js/document.getElementById "root") (ui/ui-root)))

(defn register-events! []
  (ev/register-events! rt-ref))

(defn ^:dev/after-load reload []
  (register-events!)
  (start))

(defn init []
  ;; has to be caled before start
  (register-events!)

  (local/init! rt-ref)

  (sg/run-tx! rt-ref {:e ::db/load!})

  (start))
