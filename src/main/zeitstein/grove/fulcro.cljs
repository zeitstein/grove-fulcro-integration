(ns zeitstein.grove.fulcro
  "Provides: `use-root` and `use-component`.
    
   Uses high-level API from vanilla Fulcro Raw to create Fulcro hook types for
   grove's components."
  (:require
   [shadow.experiments.grove.components :as comp]
   [shadow.experiments.grove.protocols :as gp]
   [com.fulcrologic.fulcro.raw.application :as rapp]
   [taoensso.timbre :as log]))

;; needed for lifecycle methods
(defonce listener-nums (atom 0))

(defn listener-id [ident num]
  (conj ident num))

;; based on https://github.com/thheller/shadow-experiments/blob/master/src/dev/dummy/fulcro.cljs
(deftype FulcroComponent [^:mutable ident
                          ^:mutable fulcro-comp
                          ^:mutable options
                          ^:mutable data
                          listener-num
                          fulcro-app
                          component idx]

  gp/IBuildHook
  (hook-build [this c i]
    (let [app (comp/get-env c ::app)
          num (swap! listener-nums inc)]
      (FulcroComponent. ident fulcro-comp options data num app c i)))

  gp/IHook
  (hook-init! [this]
    (log/debug "hook init" ident listener-num)
    ;; vanilla add-component! has the issue that props are memoized only *after*
    ;; they change for the first time, not on init. this is fixed in custom.
    (rapp/add-component! fulcro-app fulcro-comp
      (merge options
        {:ident ident
         :listener-id (listener-id ident listener-num)
         :receive-props (fn [new-data] ;; called immediately
                          (log/debug "hook new data" ident listener-num)
                          ;;todo i can fix the issue described above with vanilla add-component!?
                          ;; only set! and invalidate if new-data comes in
                          (set! data new-data)
                          ;; signal component to re-render
                          (comp/hook-invalidate! component idx))})))

  ;; doesn't seem to go async, no need for suspense
  (hook-ready? [this]
    true)

  (hook-value [this]
    data)

  (hook-update! [this]
    (log/debug "hook update" ident listener-num)
    true)

  (hook-deps-update! [this ^FulcroComponent new-val]
    (log/debug "hoop deps update" ident listener-num)
    (let [new-ident       (.-ident new-val)
          new-fulcro-comp (.-fulcro-comp new-val)
          new-options     (.-options new-val)]
      (if (and
            (= ident new-ident)
            (= fulcro-comp new-fulcro-comp)
            (= options new-options))
        false
        (do
          (rapp/remove-render-listener! fulcro-app (listener-id ident listener-num))

          (set! ident new-ident)
          (set! fulcro-comp new-fulcro-comp)
          (set! options new-options)

          (let [old-data data]
            (rapp/add-component! fulcro-app new-fulcro-comp
              (merge new-options
                {:ident new-ident
                 :listener-id (listener-id new-ident listener-num)
                 :receive-props (fn [new-data] ;; called immediately
                                  (log/debug "hook deps update new data" ident listener-num)
                                  (set! data new-data)
                                  ;;todo thheller says this shouldn't work, it shouldn't be done this way
                                  (comp/hook-invalidate! component idx))}))
            (not= old-data data))))))

  (hook-destroy! [this]
    (log/debug "hook destroy" ident listener-num)
    (rapp/remove-render-listener! fulcro-app (listener-id ident listener-num))))

(defn use-component
  ;;todo docstring
  "

   options are NOT 1-1 with add-component! ;;todo
   
   Mainly does:
   1. sets a listener to be called after transactions
   2. stores props queried for in an atom. Only runs callback if props have changed.
  "
  [ident fulcro-comp options]
  (FulcroComponent. ident fulcro-comp options nil nil nil nil nil))


(deftype FulcroRoot [root-key fulcro-comp options ^:mutable data app component idx]
  gp/IBuildHook
  (hook-build [this c i]
    (let [app (comp/get-env c ::app)
          listener-id (swap! listener-nums inc)
          options (merge options
                    {:listener-id [root-key listener-id]})]
      (FulcroRoot. root-key fulcro-comp options data app c i)))

  gp/IHook
  (hook-init! [this]
    (rapp/add-root! app root-key fulcro-comp
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
    ;;todo
    (throw (ex-info "shouldn't have changing deps?" {})))

  (hook-destroy! [this]
    (rapp/remove-render-listener! app (or (:listener-id options) root-key))))

(defn use-root [root-key fulcro-comp options]
  ;;todo docstring
  (FulcroRoot. root-key fulcro-comp options nil nil nil nil))
