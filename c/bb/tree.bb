(require '[cheshire.core :as ches])

(def main_tree
  [
   {:name "Root"
    :desc "the root of tad.web"
    :childs
    [{:name "Counter" :desc "counter.cljs"}
     {:name "Codemirror" :desc "cm.cljs"}
     ]
    } ])

;; 2025-12-04 assisted by copilot ===================================
(defn convert-node [path {:keys [name desc childs]}]
  (let [k (clojure.string/join "-" path)]
    (if (seq childs)
      {:key k
       :label name
       :data desc
       :icon "pi pi-fw pi-inbox"
       :expanded true
       :children (map-indexed
                   (fn [i c] (convert-node (conj path i) c))
                   childs)}
      {:key k
       :label name
       :code desc
       :icon "pi pi-fw pi-cog"
       :leaf true})))

(defn trans-tree [root]
  (map-indexed (fn [i node] (convert-node [i] node)) root))

{:body
 (-> main_tree
     trans-tree
     ches/generate-string)
:headers {"Content-Type" "text/javascript"}}

