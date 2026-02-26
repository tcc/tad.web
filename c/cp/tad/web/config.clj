(ns tad.web.config
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint]
            [taoensso.timbre :as tmbr]
            [aero.core :refer (read-config)]
            [selmer.parser :as sp]
            [tad.web.logging :as twl]))


(def D_CONFIG "config.edn")
(def D_SECRETS "secrets.edn")
(def D_FSL_CFG "etc/svc/fossil.edn")
(def D_PRM_CFG "etc/svc/prom.yml")

(def edn_cfg (atom nil))
(def edn_secrets (atom nil))

(defn get-config []
  (if (nil? @edn_cfg)
    (reset! edn_cfg (read-config D_CONFIG)))
  @edn_cfg)

(defn get-secrets []
  (if (nil? @edn_secrets)
    (reset! edn_secrets (read-config D_SECRETS)))
  @edn_secrets)

(defn make-dirs [f]
  (let [d (.getParentFile f)]
    (if-not (.exists d)
      (.mkdirs d)
      true)))

(defn get-repo [repo]
  (let [_ (get-config)
        cfg_fsl (:fossil @edn_cfg)
        {f_base :base f_user :user f_pass :pass} cfg_fsl
        f (fs/file D_FSL_CFG)
        cnt (if (fs/exists? f) (edn/read-string (slurp f)))
        cfg_r (get-in cnt [(keyword repo)])
        {r_dir :dir r_user :user} cfg_r
        is_dir (if r_dir (fs/directory? r_dir) false)
        r_pass (if r_user (get (get-secrets) r_user))
        r_pass (if r_pass r_pass f_pass)
        r_file (str f_base "/" repo ".fossil")]
    (when (and is_dir f_base f_user f_pass)
      (make-dirs (fs/file r_file))
      {:repo r_file
       :src r_dir
       :user (if r_user r_user f_user)
       :pass r_pass
       :repo-name repo
       :base-dir f_base}
      )))

(twl/defn-logged smfn_add-to-fsl
  [{:keys [repo dir user] :as params}]
  (let [f (fs/file D_FSL_CFG)
        is_dir (make-dirs f)
        _ (get-config)
        is_user (= (get-in @edn_cfg [:fossil :user]) user)
        is_user (if is_user
                  true
                  (when-not is_user
                    (get-secrets)
                    (contains? @edn_secrets user)))
        new_val (if is_user
                  {:dir dir :user user}
                  {:dir dir})
        new_cfg {(keyword repo) new_val}
        cnt (if (fs/exists? f) (edn/read-string (slurp f)))]
    (spit f (with-out-str (clojure.pprint/pprint (merge cnt new_cfg))))
    (if (doto (str/includes? (slurp f) repo)
          (#(tmbr/debug fn-corr_id {:fn (symbol fn-name) :result %})))
      params
      nil)
    ))

(defn is-user [user]
  (get-secrets)
  (contains? @edn_secrets user))

(defn get-robot []
  (let [_ (get-config)
        cfg_rbt (:robot @edn_cfg)]
    cfg_rbt))

(defn make-prom-cfg [cfg]
  (let [;;cfg (get-prom)
        f (fs/file D_PRM_CFG)]
    (when-not (fs/exists? f)
      (let [raw "global:
  scrape_interval:     15s
  evaluation_interval: 15s

rule_files:
  # - \"first.rules\"
  # - \"second.rules\"

scrape_configs:
  - job_name: {{prm-job}}
    static_configs:
      - targets: ['{{prm-web}}']
  - job_name: {{pgw-job}}
    static_configs:
      - targets: ['{{pgw-web}}']
"
            cnt (sp/render raw cfg)
            ]
        #_(println "cnt:" cnt)
        (spit f cnt)
        ))))

(defn get-prom []
  (let [_ (get-config)
        cfg_prm (:prom @edn_cfg)
        _ (make-prom-cfg cfg_prm)]
    cfg_prm))

