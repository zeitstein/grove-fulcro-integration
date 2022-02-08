(ns grove.db
  (:require
   [shadow.experiments.grove.db :as db]
   [shadow.experiments.grove.eql-query :as eql]
   [shadow.experiments.grove.events :as ev]
   [taoensso.timbre :as log]
   [generators :as gen]))

(def schema
  {::node
   {:type :entity
    :primary-key :id
    :attrs {}
    :joins {:kida [:many ::node]}}})

(defonce data-ref
  (-> {}
      (db/configure schema)
      (atom)))


(defmethod eql/attr :open-kida
  [env db {:keys [open kida]} query-part params]
  (if (and open (seq kida)) kida []))


;; event handlers
(defn load!
  {::ev/handle ::load!}
  [{:keys [db] :as tx-env}]
  (log/debug "loading data!")
  (update tx-env :db
    db/add ::node (gen/tree-denormalized 2 2) [:root]))

(defn toggle!
  {::ev/handle ::toggle!}
  [env {:keys [ident]}]
  (log/debug "toggle" ident)
  (update-in env [:db ident :open] not))

(defn text!
  {::ev/handle ::text!}
  [env {:keys [ident]}]
  (log/debug "randomize" ident)
  (assoc-in env [:db ident :text]
    (str "node: " (second ident) " " (rand))))



(comment
  (def data
    (-> {}
        (db/configure schema)
        (db/add ::node
          {:id 0
           :text "0"
           :kida [{:id 1 :text "1"}
                  {:id 2 :text "2"}]}
          [::root])))


  (db/all-idents-of data ::node)
  (db/all-of data ::node)

  (db/parse-schema schema)
  (db/parse-entity-spec ::node (::node schema))

  (-> {}
      (db/configure schema)
      (db/transacted)
      (db/add ::node
        {:id 0 :text "0" :kida [{:id 1 :text "1"}
                                {:id 2 :text "2"}]})
      (db/remove [::node 1])
      (db/commit!))
  ;
  )
