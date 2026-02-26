(require '[reagent.core :as r]
         '[reagent.dom :as rdom]
         '[re-frame.core :as rf])

(def Button js/primereact.Button)

(rf/reg-event-fx ::click (fn [{:keys [db]} _] {:db (update db :clicks (fnil inc 0))}))
(rf/reg-sub ::clicks (fn [db] (:clicks db)))

(defn my-component []
  (let [clicks (rf/subscribe [::clicks])]
    [:div
      [:p "Clicks: " @clicks]
      [:p [:> Button {:on-click #(rf/dispatch [::click])}
            "Click me! 測試"]]]))

(rdom/render [my-component] (.getElementById js/document "main"))
