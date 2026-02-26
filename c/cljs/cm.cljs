(require '[clojure.string :as str])
(declare cm)
(declare doc)

(require '[reagent.core :as r]
         '[reagent.dom :as rdom]
         '[re-frame.core :as rf])

(def Button js/primereact.Button)

(defn eval-me []
  (js/scittle.core.eval_string (-> cm .-state .-doc .toString)))

(def extension
  (.of js/cv.keymap
       (clj->js [{:key "Mod-Enter"
                  :run (fn []
                         (eval-me))}
                 #_{:key (str modifier "-Enter")
                    :shift (partial eval-top-level on-result)
                    :run (partial eval-at-cursor on-result)}])))

(def doc (str/trim "
(require '[reagent.core :as r]
         '[reagent.dom :as rdom]
         '[re-frame.core :as rf])

(def Button js/primereact.Button)

(rf/reg-event-fx ::m-click (fn [{:keys [db]} _] {:db (update db :m-clicks (fnil inc 0))}))
(rf/reg-sub ::m-clicks (fn [db] (:m-clicks db)))

(defn my-component []
  (let [clicks (rf/subscribe [::m-clicks])]
    [:div
      [:p \"Clicks: \" @clicks]
      [:> Button {:on-click #(rf/dispatch [::m-click]) :class \"btn btn-primary\"}
            \"Click me!!\"]]))

(rdom/render [my-component] (.getElementById js/document \"main-reagent\"))
"))


(defn linux? []
  (some? (re-find #"(Linux)|(X11)" js/navigator.userAgent)))

(defn mac? []
  (and (not (linux?))
       (some? (re-find #"(Mac)|(iPhone)|(iPad)|(iPod)" js/navigator.platform))))

;;(set! (.-main_eval_me js/globalThis) eval-me)



(defn my-component []
  (let []
    [:div
      [:h2 "Codemirror"]
      [:div {:id "main-app"}]
     [:div {:id "main-reagent"
            :class "border border-warning my-2 p-2"}]
      [:> Button {:on-click #(eval-me) :id "main-evalMe" :class "btn btn-primary"} "Eval"]
    ]))

(rdom/render [my-component] (.getElementById js/document "main"))

(def cm
  (let []
    (js/cm.EditorView. #js {:doc doc
                            :extensions #js [js/cm.basicSetup, js/od.oneDark, (js/lc.clojure), (.highest js/cs.Prec extension)]
                            :parent (js/document.querySelector "#main-app")
                            #_#_:dispatch (fn [tr] (-> cm (.update #js [tr])) (eval-me))
                            })))
;;(set! (.-cm_instance js/globalThis) cm)

(let [elt (js/document.getElementById "main-evalMe")
      txt (.-innerText elt)
      mod-symbol (if (mac?)
                   "⌘"
                   "⌃")
      txt (str txt " " mod-symbol"-⏎")]
  (set! (.-innerHTML elt) txt))

(eval-me)
