(ns development-preload
  (:require
   [com.fulcrologic.fulcro.algorithms.timbre-support :as ts]
   [taoensso.timbre :as log]
   [devtools.core :as devtools]))

(def lvl :info)
(js/console.log (str "Turning logging to " lvl " (in development-preload)"))
(log/set-level! lvl)
(log/merge-config! {:output-fn ts/prefix-output-fn
                    :appenders {:console (ts/console-appender)}})

;; colors for dark dev tools
(let [{:keys [cljs-land-style]} (devtools/get-prefs)]
  (devtools/set-pref! :cljs-land-style (str "filter:invert(1);" cljs-land-style)))
(devtools/install!)
