(ns grove.ui
  "for comparison, example-ui using grove query + db"
  (:require
   [shadow.experiments.grove :as sg :refer (<< defc)]
   [taoensso.timbre :as log]
   [grove.db :as db]))

;; Performance (dev build)
;;
;; toggling 5 5 tree (4k nodes) at root  (reading the violation "handler took" message :))
;; ~170ms most frequently, sometimes >300ms
;; using ui-node-eql maybe slows it down a bit ~10ms?
;;
;; tx! 5 5 tree at 0-4-4-4-4-4 from click to hook setting new data
;; (logs in hook-update! after .do-read!)
;; ~instant! max 1ms
;; using ui-node-eql doesn't measurably slow it down
;;
;; toggling 4 4 (341 nodes) tree at root, from click to render effect
;; (hook (if (= id "0-3") (sg/render-effect #(js/console.log id))))
;; 20-30ms
;;
;; tx! 4 4 tree at 0-3-3-3-3 from click to hook setting new data
;; ~instant! max 1ms
;;
;; awesome!

(defc ui-node [ident]
  (bind {:keys [id open text kida]}
    (sg/query-ident ident))

  (render
    (<< [:div
         [:button {:on-click {:e ::db/toggle! :ident ident}} (str open)]
         [:button {:on-click {:e ::db/text! :ident ident}} "tx!"]
         [:span text]
         (when (and open (seq kida))
           (<< [:div {:style "margin-left: 20px;"}
                (sg/keyed-seq kida identity ui-node)]))])))

(defc ui-node-eql [ident]
  (bind {:keys [id open text open-kida]}
    (sg/query-ident ident [:id :open :text :open-kida]))

  (render
    (<< [:div
         [:button {:on-click {:e ::db/toggle! :ident ident}} (str open)]
         [:button {:on-click {:e ::db/text! :ident ident}} "tx!"]
         [:span text]
         [:div {:style "margin-left: 20px;"}
          (sg/keyed-seq open-kida identity ui-node)]])))

(defc ui-root []
  (bind {:keys [root]}
    (sg/query-root [:root]))

  (render
    (ui-node (first root))))
