(ns ui-vanilla
  (:require
   [fulcro.vanilla :as f]
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
;; component 360-440ms
;; root 200-300ms (of course, no need to add/remove listeners,
;; but will over-denormalize in examples where not all nodes are open)
;;
;; tx! 5 5 tree at 0-4-4-4-4-4 from click to hook setting new data
;; component 30-40ms
;; root 50-60ms
;; improved with tx!! in custom

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
    (f/use-component ident Node {:initialize? false}))

  (render
    (<< [:div
         [:button {:on-click {:e ::toggle! :id id}} (str open)]
         [:button {:on-click {:e ::test! :id id}} "tx!"]
         [:button {:on-click {:e ::test!! :id id :ident ident}} "tx!!"]
         [:span text]
         (when (and open (seq kida))
           (<< [:div {:style "margin-left: 20px;"}
                (sg/keyed-seq kida identity use-component-node)]))])))


(defn use-root-node [{:keys [id open text kida]}]
  (<< [:div
       [:button {:on-click {:e ::toggle! :id id}} (str open)]
       [:button {:on-click {:e ::test! :id id}} "tx!"]
       [:button {:on-click {:e ::test!! :id id :ident [:id id]}} "tx!!"]
       [:span text]
       (when (and open (seq kida))
         (<< [:div {:style "margin-left: 20px;"}
              (sg/keyed-seq kida :id use-root-node)]))]))

(defc ui-root []
  ;; does not fit grove's philosophy, but can be used
  (bind data-tree (f/use-root ::root-node Tree {:initialize false}))

  (render (<< [:h2 "use-component"]
              (use-component-node [:id "0"])

              [:h2 "use-root"]
              (use-root-node data-tree)))

  ;;todo use render-target
  ;; events will bubble up
  (event ::toggle! [{::f/keys [app] :as env} {:keys [id]}]
    (log/debug "click!")
    (rc/transact! app [`(toggle! {:id ~id})]))

  (event ::test! [{::f/keys [app]} {:keys [id]}]
    (log/debug "click!")
    (rc/transact! app [`(text! [:id ~id])]))

  ;; doesn't work as intended – all listeners will be called (2022-02-05)!
  ;; enabling this is the main work in custom 
  (event ::test!! [{::f/keys [app]} {:keys [id ident]}]
    (log/debug "click!")
    (rc/transact!! app [`(text! [:id ~id])] {:only-refresh [ident]})))


(defn ^:dev/after-load start []
  (sg/render f/rt-ref (js/document.getElementById "root") (ui-root)))

(defn init []
  #_(inspect/app-started! (f/APP))

  (merge/merge-component! (f/APP) Tree (gen/tree-denormalized 2 2)
    :replace [::root-node])

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


