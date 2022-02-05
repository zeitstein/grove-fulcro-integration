(ns ui-custom
  (:require
   [fulcro.custom :as f]
   [generators :as gen]
   [shadow.experiments.grove :as sg :refer (<< defc)]
   [shadow.experiments.grove.runtime :as rt]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.inspect.inspect-client :as inspect]
   [taoensso.timbre :as log]))

;; Performance
;;
;; toggling 5 5 tree at root
;; component 360-440ms (to be expected, all of the optimizations are for targeted refreshes)
;;
;; tx! 5 5 tree at 0-4-4-4-4-4 from click to hook setting new data
;; component 30-40ms (to be expected, it's basically the same impl up to a fix not relevant here)
;; component-s 50ms (= vs. identical?)
;; (tx with wildcard instead of Node doesn't do much in this example)
;;
;; tx!! 5 5 tree at 0-4-4-4-4-4 from click to hook setting new data
;; component 2-3ms
;; component-s 2-3ms


(def Node (rc/nc [:id :open :text :kida]))
(def Tree (rc/nc [:id :open :text {:kida `...}]))

(defmutation toggle! [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:id id :open] not)))

(defmutation text! [{:keys [id text]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [:id id :text] (str (rand)))))


(defc use-component-node [ident]
  (bind {:keys [id open text kida listener-id]}
    (f/use-component ident Node {}))

  (render
    (<< [:div
         [:button {:on-click {:e ::toggle! :id id}} (str open)]
         [:button {:on-click {:e ::test! :id id}} "tx!"]
         [:button {:on-click {:e ::test!! :id id :ident ident}} "tx!!"]
         [:span text]
         (when (and open (seq kida))
           (<< [:div {:style "margin-left: 20px;"}
                (sg/keyed-seq kida identity use-component-node)]))])))

(defc ui-root []
  (render (<< [:h2 "custom"]
              (use-component-node [:id "0"])))

  ;;todo use render-target
  ;; events will bubble up
  (event ::toggle! [{::f/keys [app] :as env} {:keys [id]}]
    (log/debug "click!")
    (rc/transact! app [`(toggle! {:id ~id})]))

  (event ::test! [{::f/keys [app]} {:keys [id]}]
    (log/debug "click!")
    (rc/transact! app [`(text! [:id ~id])]))

  (event ::test!! [{::f/keys [app]} {:keys [id ident]}]
    (log/debug "click!")
    ;; will call only listeners of ident
    (rc/transact!! app [`(text! [:id ~id])] {:grove-refresh ident})))


(defn ^:dev/after-load start []
  (sg/render f/rt-ref (js/document.getElementById "root") (ui-root)))

(defn init []
  #_(inspect/app-started! (f/APP))

  (merge/merge-component! (f/APP) Tree (gen/tree-denormalized 2 2))
  ;; inc fulcro app in component env
  (swap! f/rt-ref update ::rt/env-init conj #(assoc % ::f/app (f/APP)))

  (start))


(comment
  (-> (f/APP)
      (:com.fulcrologic.fulcro.application/runtime-atom)
      (deref)
      (:com.fulcrologic.fulcro.application/render-listeners))
  ;
  )


