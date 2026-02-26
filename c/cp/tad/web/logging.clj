(ns tad.web.logging
  (:require [taoensso.timbre :as tmbr]
            [cheshire.core :as ches])
  (:import [java.util TimeZone UUID]
           [java.time ZoneId]))

(defonce old-config tmbr/*config*)
(defonce tz (TimeZone/getTimeZone (ZoneId/systemDefault) #_"Asia/Taipei"))

(defn json-output-fn
  [{:keys [vargs hostname_ timestamp_ instant level msg_ ?file ?line ?column] :as args}]
  (let [args (reduce conj (if (map? (first vargs)) {} []) vargs)
        res (:res args)
        args (if (and res (:content res)) (assoc-in args [:res :content] "...") args)
        ;; remove cnt from .log
	rets {:timestamp @timestamp_ :level level :d args}
	;;rets (if (and ?file ?line ?column)
        ;;       (conj rets {:source {:file ?file :line ?line :column ?column}})
        ;;       rets)
        ]
    (ches/generate-string rets)))

(defn init []
  (tmbr/merge-config!
   {:timestamp-opts {:timezone tz}
    :min-level :trace
    :appenders
    {;;:println {:enabled? false}
     :spit
     (merge
      (tmbr/spit-appender
       {:fname "tadweb.log"}) {:output-fn json-output-fn})
     }}))

(defn fini []
  (tmbr/set-config! old-config))


(defn corr-id []
  (UUID/randomUUID))

;; from gemini #AI
(defmacro defn-logged [name args & body]
  `(defn ~name ~args
     (let [~'fn-name ~(str name)
           ~'fn-corr_id (get-in ~args [0 :as :corr_id])]
       ;;(println "Start:" ~'fn-name)
       (if ~'fn-corr_id
         (tmbr/trace ~'fn-corr_id {(keyword ~'fn-name) :begin})
         (tmbr/trace ~'fn-name "begin"))
       (let [~'fn-res ~@body]
         (if ~'fn-corr_id
           (tmbr/trace ~'fn-corr_id {(keyword ~'fn-name) :end
                                     :result ~'fn-res})
           (tmbr/trace ~'fn-name "end" (str ~'fn-res)))
         ~'fn-res))))
