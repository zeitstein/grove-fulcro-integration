(ns generators)

(defn node
  [{:keys [id text open kida]
    :or   {id (rand)
           text (str "node: " id)
           open true}}]
  (let [node {:id   id
              :text text
              :open open}]
    (cond-> node
      kida (assoc :kida kida))))

(defn tree-denormalized [num-kida max-tree-depth & {:keys [id] :or {id "0"} :as defaults}]
  (letfn [(tree-node [id depth]
            (if (= depth max-tree-depth)
              (node (merge defaults {:id id}))
              (assoc (node (merge defaults {:id id}))
                :kida (mapv #(tree-node (str id "-" %)
                               (inc depth))
                        (range num-kida)))))]
    (tree-node id 0)))
