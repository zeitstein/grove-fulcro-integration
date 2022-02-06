(ns zeitstein.grove.fulcro
  "Provides: `use-root` and `use-component`.
    
   Uses API from vanilla Fulcro Raw to create custom Fulcro hook types for
   grove's components."
  (:require
   [shadow.experiments.grove.components :as comp]
   [shadow.experiments.grove.protocols :as gp]
   [shadow.experiments.grove.db :as db]
   [shadow.experiments.grove.runtime :as rt]
   [com.fulcrologic.fulcro.raw.application :as rapp]
   [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as stx]
   [taoensso.timbre :as log]))

(defonce listener-ids (atom 0))

;; based on https://github.com/thheller/shadow-experiments/blob/master/src/dev/dummy/fulcro.cljs
(deftype FulcroComponent [ident model options ^:mutable data app component idx]
  gp/IBuildHook
  (hook-build [this c i]
    (let [app (comp/get-env c ::app)
          listener-id (swap! listener-ids inc)
          options (merge options {:ident ident
                                  :listener-id [ident listener-id]})]
      (FulcroComponent. ident model options data app c i)))

  gp/IHook
  (hook-init! [this]
    (rapp/add-component! app model
      (assoc options :receive-props
        (fn [new-data]
          (log/debug "hook new data")
          (set! data new-data)
          ;; signal component to re-render
          (comp/hook-invalidate! component idx)))))

  ;; doesn't seem to go async, no need for suspense
  (hook-ready? [this]
    true)

  (hook-value [this]
    data)

  (hook-update! [this]
    true)

  (hook-deps-update! [this new-val]
    ;;todo thheller: probably ok to have changing deps
    ;; but I don't know what I'd need to call in fulcro to tell it
    (throw (ex-info "shouldn't have changing deps?" {})))

  (hook-destroy! [this]
    (rapp/remove-render-listener! app (or (:listener-id options) ident))))

(defn use-component
  ;;todo docstring
  "
   From grove user's perspective...
   
   Mainly does:
   1. sets a listener to be called after transactions
   2. stores props queried for in an atom. Only run callback if props have changed.
  "
  [ident model options]
  (FulcroComponent. ident model options nil nil nil nil))


(deftype FulcroRoot [root-key model options ^:mutable data app component idx]
  gp/IBuildHook
  (hook-build [this c i]
    (let [app (comp/get-env c ::app)
          listener-id (swap! listener-ids inc)
          options (merge options
                    {:listener-id [root-key listener-id]})]
      (FulcroRoot. root-key model options data app c i)))

  gp/IHook
  (hook-init! [this]
    (rapp/add-root! app root-key model
      (assoc options
        :receive-props
        (fn [new-data]
          (log/debug "hook new data")
          (set! data new-data)
          ;; signal component to re-render
          (comp/hook-invalidate! component idx)))))

  (hook-ready? [this]
    true)

  (hook-value [this]
    (get data root-key))

  (hook-update! [this]
    true)

  (hook-deps-update! [this new-val]
    ;;todo thheller: probably ok to have changing deps
    ;; but I don't know what I'd need to call in fulcro to tell it
    (throw (ex-info "shouldn't have changing deps?" {})))

  (hook-destroy! [this]
    (rapp/remove-render-listener! app (or (:listener-id options) root-key))))

(defn use-root [root-key model options]
  ;;todo docstring
  (FulcroRoot. root-key model options nil nil nil nil))