(ns fulcro.custom
  "Enabling targeted refreshes (declared on `transact!!`)"
  (:require
   [shadow.experiments.grove.components :as comp]
   [shadow.experiments.grove.protocols :as gp]
   [shadow.experiments.grove.db :as db]
   [shadow.experiments.grove.runtime :as rt]
   [com.fulcrologic.fulcro.raw.application :as rapp]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
   [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as stx]
   [com.fulcrologic.fulcro.algorithms.lookup :as ah]
   [taoensso.timbre :as log]))

;;todo extract this elsewhere?
(defonce data-ref
  (-> {}
      (db/configure {})
      (atom)))

(declare APP)


(defn refresh-component! [ident]
  ;; because the listener-ids have 'random bits' one would have to filter the list
  ;; of all listeners (to find idents in listener ids, which might not be much more
  ;; performant than just running all callbacks (at least, if they use ref compares?)
  ;; so, in this file I hack around to get fulcro to store listeners in the form:
  ;; {ident {:listener1 callback1 :listener2 callback2}}

  ;;todo support idents *and* components
  ;; i.e. allow :refresh-ident vs :refresh-component functionalities
  (let [ident-callbacks (-> (APP)
                            :com.fulcrologic.fulcro.application/runtime-atom
                            deref
                            :com.fulcrologic.fulcro.application/render-listeners
                            (get ident))]
    (log/debug "calling listeners with" ident)
    (doseq [callback (vals ident-callbacks)]
      (callback))))

(defn render! [app options]
  (let [runtime-atom (:com.fulcrologic.fulcro.application/runtime-atom app)
        batch-notifications (ah/app-algorithm app :batch-notifications)
        listeners (-> runtime-atom deref :com.fulcrologic.fulcro.application/render-listeners vals)
        notify-all!         (fn []
                              ;;todo you can do better than nested doseq...
                              (doseq [ident-val listeners]
                                (if (fn? ident-val) ;; handle application-rendered!
                                  (ident-val app options)
                                  (doseq [render-listener (vals ident-val)]
                                    (render-listener app options)))
                              ;;todo
                                ;;  (try
                              ;;      (render-listener app options)
                              ;;      (catch #?(:clj Exception :cljs :default) e
                              ;;        (log/error e "Render listener failed. See https://book.fulcrologic.com/#err-render-listener-failed")))
                                ))]
    (if batch-notifications
      (batch-notifications notify-all!)
      (notify-all!))))

(defn add-render-listener!
  [app ident listener-id listener]
  (swap! (:com.fulcrologic.fulcro.application/runtime-atom app) assoc-in
    [:com.fulcrologic.fulcro.application/render-listeners ident listener-id] listener))

(defn remove-render-listener!
  [app ident listener-id]
  (swap! (:com.fulcrologic.fulcro.application/runtime-atom app) update-in
    [:com.fulcrologic.fulcro.application/render-listeners ident] dissoc listener-id))


(defonce rt-ref
  (let [fulcro-app
        (-> (stx/with-synchronous-transactions
              (rapp/fulcro-app {#_#_:submit-transaction! #(js/console.log "tx!") ;; doesn't work because of a bug in fulcro with-sync-tx overwrite submit-transaction! from fulcro-app
                                :refresh-component! refresh-component!
                                :render-root!      (constantly true)
                                :optimized-render! (constantly true)}))
            (assoc-in ;; goes around default schedule-render! debouncing for faster tx processing
              ;;todo actually, this might not be needed...
              [:com.fulcrologic.fulcro.application/algorithms
               :com.fulcrologic.fulcro.algorithm/schedule-render!]
              render!)
            (assoc-in ;; a hack to get refresh-component! working because raw + sync is buggy
              [:com.fulcrologic.fulcro.application/algorithms
               :com.fulcrologic.fulcro.algorithm/tx!]
              (fn [app tx options]
                (stx/submit-sync-tx! app tx (assoc options :component (:grove-refresh options)))))
            (assoc-in ;; someplace render! is hard-coded, but elsewhere this also gets used
              [:com.fulcrologic.fulcro.application/algorithms
               :com.fulcrologic.fulcro.algorithm/render!]
              render!))]
    (rt/prepare {::app fulcro-app} data-ref ::rt-id)))

(defn APP [] (::app @rt-ref))


(defonce listener-ids (atom 0))

(defn add-component-s!
  [app component {:keys [receive-props listener-id] :as options}]
  (let [ident       (:ident options)
         ;;todo you can do traced props if here if you don't use dnm
         ;; i.e. just get the map under ident, and that can be compared with identical?
        get-props   #(fdn/db->tree (rc/get-query component) ident (rapp/current-state (APP)))
        prior-props (atom (get-props))]
    (receive-props (get-props))
    (add-render-listener! app ident listener-id
      (fn [app _]
        ;; (log/debug "listener" ident)
        (let [props (get-props)]
          (when-not (= @prior-props props)
            (reset! prior-props props)
            (receive-props props)))))))

(defn add-component!
  ;; 'memoizes' props on init, not after first tx like in vanilla
  ;;todo i think this can be `add-query!` and `use-query` in API
  ;; want to use EQL not fulcro components, more like grove's
  ;; will need custom get-traced-props, at least
  [app component {:keys [receive-props listener-id] :as options}]
  (let [ident       (:ident options)
        prior-props (atom nil)
        get-traced-props #(rc/get-traced-props (rapp/current-state (APP)) component ident @prior-props)
        current-props #(reset! prior-props (get-traced-props))]
    (receive-props (current-props))
    (add-render-listener! app ident listener-id
      (fn [app _]
        ;; (log/debug "listener" ident listener-id)
        (let [old @prior-props
              props (current-props)]
          (when-not (identical? old props)
            (reset! prior-props props)
            (receive-props props)))))))


(deftype FulcroComponent [ident model options ^:mutable data app component idx]
  gp/IBuildHook
  (hook-build [this c i]
    (let [app (comp/get-env c ::app)
          listener-id (swap! listener-ids inc)
          options (merge options {:ident ident
                                  :listener-id listener-id})]
      (FulcroComponent. ident model options data app c i)))

  gp/IHook
  (hook-init! [this]
    (add-component! app model
      (assoc options :receive-props
        (fn [new-data]
          (log/debug "hook new data" (:ident options) (:listener-id options))
          (set! data
            ;; to be able to use it with refresh-component
            ;;todo namespace it, to differentiate from db data?
            (assoc new-data :listener-id (:listener-id options)))
          ;; signal component to re-render
          (comp/hook-invalidate! component idx)))))

  ;; doesn't seem to go async, no need for suspense
  (hook-ready? [this]
    true)

  (hook-value [this]
    data)

  ;; don't know if there is a way to pull data ouf of the state
  ;; this will be triggered at render-time when component is just before update
  ;; but we already did the work in the :receive-props callback
  (hook-update! [this]
    true)

  ;; this would be called when the arguments to use-root changed
  (hook-deps-update! [this new-val]
    ;;todo thheller: probably ok to have changing deps
    ;; but I don't know what I'd need to call in fulcro to tell it
    (throw (ex-info "shouldn't have changing deps?" {})))

  (hook-destroy! [this]
    ;; (js/console.log "removing hoook" (or (:listener-id options) ident))
    (remove-render-listener! app ident (:listener-id options))))

(defn use-component [ident model options]
  (FulcroComponent. ident model options nil nil nil nil))
