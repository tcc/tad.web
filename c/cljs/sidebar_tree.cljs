(require 
 '[clojure.string :as str]
 '[reagent.core :as r]
 '[reagent.dom :as rdom]
 '[ajax.core :refer [GET] :as ajax])

(def Tree js/primereact.Tree)
(def BreadCrumb js/primereact.BreadCrumb)

(def main_tree-data [{:key "0" :label "root" :leaf false}])
(defonce main_state (r/atom main_tree-data))
(defonce main_loading (r/atom false))
(defn main_handler [response]
  (comment js/alert (js->clj (.parse js/JSON response) :keywordize-keys true))
  (reset! main_state (js->clj (.parse js/JSON response) :keywordize-keys true)))

(defn full-names [p_key p_da]
  (loop [kl (map #(int %)(str/split p_key #"-"))
	 da p_da 
	 res []] 
    (let [;;_ (println " ::kl->" kl)
          ;;_ (println " ::da->" da)
          k1 (first kl)
	  kr (rest kl)
	  k1e (get-in da [k1])
	  kre (:children k1e)
	  _res (conj res (:label k1e))
          ;;_ (println " ::res->" _res)
	  ]
      (if (and kre kr (-> kr count (> 0)))
	(recur kr kre _res)
	_res)
      ))
  )

(defn main_on-node-expand [event]
  (let [node (.-node event)
	key (.-key node)
	children (.-children node)]
    (js/console.log (str "node: " node))
    (js/console.log (str "key: " key))
    (if (nil? children)
      (let [names (rest (full-names key @main_state))
            _ (println "names=>" names)
            ]
	(js/console.log (str "children: " children))
	(reset! main_loading true)
	(js/setTimeout
	 (fn []
	   (let [ks (mapcat #(conj [] % :children) (map #(int %) (str/split key #"-")))
		 new-nodes [{:key (str key "-0") :label "child 1" :leaf false}
			    {:key (str key "-1") :label "child 2" :leaf false}]]
	     (swap! main_state #(update-in @main_state ks :children new-nodes))
	     (reset! main_loading false)
	     ))
	 100))))) ;; Simulate delay

(def bc_items (r/atom nil))
(def bc_home (r/atom
              {:label "home"
               :template (fn [] (r/as-element [:a {:href "/" :class "pi pi-home"}]))}))

(defn breadc []
  [:> BreadCrumb {:model @bc_items :home @bc_home :unstyled false
                  :style {"padding" "0" "background" "transparent"
                          "border" "0" "margin-bottom" "0"}}])

(defn main_on-select [event]
  (let [node (.-node event)
	key (.-value event)
	ks (vec (rest (mapcat #(conj [:children] %) (map #(int %) (str/split key #"-")))))
	curr (get-in @main_state ks)
	code_url (if (nil? (:code curr)) nil (str "/c/cljs/" (:code curr)))]
    
    (let [names (rest (full-names key @main_state))
          _ (println "names->" (str/join "/" names))
          _ (reset! bc_items (map #(array-map :label %) names))
          ]
      (when-not (nil? code_url)
        (rdom/render [breadc] (.getElementById js/document "tad.breadcrumb"))
        (ajax/GET code_url {:handler (fn [res] (js/scittle.core.eval_string (str res)))})
        ))
    ))

(defn main_tree-component []
  [:> Tree
   {:value @main_state
    :onExpand main_on-node-expand
    :onSelectionChange main_on-select
    :selectionMode "single"
    :loading @main_loading}])

(rdom/render [main_tree-component] (.getElementById js/document "tree"))


(ajax/GET "/c/bb/tree.bb" {:handler main_handler})
