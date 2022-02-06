(ns ui-vanilla
  "Illustrates two approaches:
    1. Similar to Fulcro `use-root-root` gets the whole denormalized data
       (using the root query) tree and passes it down.
    2. `use-component-root` queries for and only passes down the root-node *ident*,
        and so on down the component tree. In other words, components query
        directly for data they need by ident. (Conceptually similar to how one
        would use Fulcro + React hooks.)
    
    For simplicity, only client-side. But, most anything you know from Fulcro
    (unrelated to rendering) could be used."
  (:require
   [fulcro.vanilla :as f] ;;todo change file structure to grove-fulcro.vanilla
   [generators :as gen]
   [shadow.experiments.grove :as sg :refer (<< defc)]
   [shadow.experiments.grove.runtime :as rt]
   [com.fulcrologic.fulcro.raw.application :as rapp]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.inspect.inspect-client :as inspect]
   [taoensso.timbre :as log]))

;; Performance
;;
;; toggling 5 5 tree (4k nodes) at root
;; component 360-440ms
;; root 200-300ms (of course, no need to add/remove listeners)
;; (but will over-denormalize, inc. nodes which are not open (visible in ui))
;;
;; tx! 5 5 tree at 0-4-4-4-4-4 from click to hook setting new data
;; component 30-40ms
;; root 50-60ms
;; improved with tx!! in custom
;;
;; toggling 4 4 (341 nodes) tree at root
;; component 40-50ms
;; root 15-25ms
;;
;; tx! 4 4 tree at 0-3-3-3-3 from click to hook setting new data
;; component 3-4ms
;; root 6-10ms

(defmutation toggle! [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:id id :open] not)))

(defmutation text! [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:id id :text] #(str "node: " id "-" (rand)))))


(defn common [id open text ident]
  (<< [:button {:on-click {:e ::toggle! :id id}} (str open)]
      [:button {:on-click {:e ::test! :id id}} "tx!"]
      [:button {:on-click {:e ::test!! :id id :ident ident}} "tx!!"]
      [:span text]))

(def Node (rc/nc [:id :open :text :kida])) ;; note: no join/recursion on :kida

;; queries for what it needs directly
(defc use-component-node [ident]
  (bind {:keys [id open text kida listener-id]}
    (f/use-component ident Node {:initialize? false}))

  (render
    (<< [:div
         (common id open text ident)
         (when (and open (seq kida))
           (<< [:div {:style "margin-left: 20px;"}
                (sg/keyed-seq kida identity use-component-node)]))])))

(defc use-component-root []
  ;;todo ideally would just get {::root-node [:id "0"]}, but don't know how
  (bind {:keys [id]}
    (f/use-component [::root-node] (rc/nc [:id]) {:initialize? false}))

  (render (<< [:h3 "use-component"]
              (use-component-node [:id id]))))


;; gets props from parent
(defn use-root-node [{:keys [id open text kida]}]
  (<< [:div
       (common id open text [:id id])
       (when (and open (seq kida))
         (<< [:div {:style "margin-left: 20px;"}
              (sg/keyed-seq kida :id use-root-node)]))]))

;; recursive query apt for this example, otherwise could compose component queries
(def Root (rc/nc [:id :open :text {:kida `...}]))

;; standard fulcro approach - root queries for the complete (denormalized) data tree
(defc use-root-root []
  ;; EQL [{::root-node (rc/get-query Root)}]
  (bind data-tree (f/use-root ::root-node Root {:initialize false}))

  (render (<< [:h3 "use-root"]
              (use-root-node data-tree))))


(defc ui-root []
  (render (<< (use-component-root)
              (use-root-root)))

  ;;todo use grove's render-target
  ;; events will bubble up
  (event ::toggle! [{::f/keys [app] :as env} {:keys [id]}]
    (log/debug "click!")
    (rc/transact! app [`(toggle! {:id ~id})]))

  (event ::test! [{::f/keys [app]} {:keys [id]}]
    (log/debug "click!")
    (rc/transact! app [`(text! [:id ~id])]))

  ;; doesn't work as intended – all listeners will be called!
  ;; (refresh-component! is broken in raw (2022-02-05))
  ;; enabling this is the main work in custom 
  (event ::test!! [{::f/keys [app]} {:keys [id ident]}]
    (log/debug "click!")
    (rc/transact!! app [`(text! [:id ~id])] {:only-refresh [ident]})))


;; grove runtime
(defonce rt-ref
  (rt/prepare
    {::f/app (rapp/headless-synchronous-app Root)}
    nil ;; data-ref not used here
    ::rt-id))

(defn ^:dev/after-load start []
  (sg/render rt-ref (js/document.getElementById "root") (ui-root)))

;; helper
(defn app [] (::f/app @rt-ref))

(defn init []
  ;; use fulcro inspect
  (inspect/app-started! (app))

  ;; simulates load!
  (merge/merge-component! (app) Root (gen/tree-denormalized 2 2)
    :replace [::root-node])

  ;; add fulcro app to component env
  (swap! rt-ref update ::rt/env-init conj #(assoc % ::f/app (app)))

  (start))


(comment
  (-> (app)
      (:com.fulcrologic.fulcro.application/runtime-atom)
      (deref)
      (:com.fulcrologic.fulcro.application/render-listeners))

  ;;todo change root node doesn't work (need changing deps)
  (merge/merge-component! (app) Node [:id "0-0"] :replace [::root-node])
  ;
  )


