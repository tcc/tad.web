(ns tad.web.svc
  (:require [org.httpkit.server :as http]
            [integrant.core :as ig]
            [ruuter.core :as ruuter]
            [taoensso.timbre :as tmbr]
            [tad.web.logging :as twl]
            [tad.web.store :refer [timeout-store]]))

;; ref. https://github.com/trhura/clojure-term-colors/blob/master/src/clojure/term/colors.clj
(defn escape-code [i] (str "\033[" i "m"))

(defmethod ig/init-key :tad.web/server [_ {:keys [port handler start]}]
  (println (str "URL: "
                (escape-code 4)
                "http://127.0.0.1:" port start
                (escape-code 0)))
  (twl/init)
  (tmbr/info "Starting svc on port" port)
  (http/run-server handler {:port port}))

(defmethod ig/halt-key! :tad.web/server [_ server]
  (tmbr/info "Stop svc")
  (server :timeout 100)
  (twl/fini))

(comment defmethod ig/init-key :tad.web/handler [_ {:keys [routes]}]
  (ruuter/handler routes))

(defmethod ig/init-key :tad.web/router [_ {:keys [routes]}]
  ;;(println "routes->" routes)
  (fn [req]
    (let [req_cid (-> req
                      (assoc :corr-id (str (twl/corr-id)))
                      ;;(dissoc :async-channel)
                      )]
      ;;(println req_cid)
      ;;(tmbr/info req_cid)
      (ruuter/route routes req_cid))))
  
(defmethod ig/init-key :tad.web.handle/hello [_ _]
  (fn [req]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body "Hello, world!"}))

(defmethod ig/init-key :tad.web/kvs [_ _]
  (timeout-store))
