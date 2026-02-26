#!/usr/bin/env bb
(require '[babashka.http-client :as http])
(require '[babashka.fs :as fs])
(require '[clojure.string :as str])
(require '[clojure.java.io :as io])

(def out-dir (fs/file "w"))
(def m2x {"scittle" ["scittle@0.7.30" "scittle@0.7.31" "scittle@0.8.31"]
          "react" ["react@18.3.1" "react-dom@18.3.1"
                   "primeicons@7.0.0"
                   "react-transition-group@4.4.5"
                   "primereact@10.9.7" "primeflex@4.0.0"]
          "cm" ["codemirror@6.0.2"
                "@codemirror" "@nextjournal" "@lezer" "@marijn"]
          "bs" ["bootstrap@5.3.8" "bootstrap-icons@1.13.1" "5.3" "5.2" "@popperjs"]
          "qb" ["prismjs@1.30.0" "prism-theme-github@1.0.2"]
          "docs.css" ["_slug_.CJzlHdSW.css"]
          "docs.min.js" ["Scripts.astro_astro_type_script_index_0_lang.B072HICR.js"]})
(def x2m (reduce-kv #(into %1 (for [i %3] [i %2])) {} m2x))

(defn url-dl [a-url]
  (let [u (io/as-url a-url)
        uf (fs/file (.getFile u))
        flst (map #(if-let [xm (get x2m %)] xm %) 
                  (-> (.getPath uf) (str/split #"/")
                      ((fn [l]
                         (filter
                          #(-> (contains? #{"" "npm" "src" "dist"
                                            "umd" "docs"
                                            "keep-markup" "plugins"}
                                          %) not)
                          l)))))
        fname (last flst)
        fpath (str/join fs/file-separator (drop-last flst))
        out-dir (fs/file out-dir fpath)
        _ (println "dir" out-dir)
        _ (fs/create-dirs out-dir)
        ]
    (println (str "Downloading " a-url " to " fname))
    (try
      (io/copy
       (:body (http/get a-url {:as :stream}))
       (fs/file out-dir fname))
      (println (str "  Successfully downloaded file of size: " (.length (fs/file out-dir fname)) " bytes"))
      (catch Exception e
        (println (str "  An error occurred: " (.getMessage e)))))
    ))


(println "All arguments:" *command-line-args*)
;;(url-dl url)

;; 2026-02 mod: violet -> flat
(defn mod_violet_2_flat []
  (let [bsdocs (fs/file out-dir "bs/assets/docs.css")
        bsdocs_bk (fs/file out-dir "bs/assets/docs_bk.css")]
    (when (fs/exists? bsdocs)
      (fs/move bsdocs bsdocs_bk {:replace-existing true})
      (let [cnt (slurp bsdocs_bk)]
        (spit (str bsdocs)
              (-> cnt 
	          (str/replace #"#4c0bce" "#E3DBD9")
	          (str/replace #"113, 44, 249" "171,155,139")
	          (str/replace #"#712cf9" "#C8BCB0")
	          (str/replace #"bd-violet" "bd-flax"))
	      )))))

(loop [args *command-line-args*]
  (when-let [a-txt (fs/file (first args))]
    (when (fs/exists? a-txt)
      (println "URL list file:" (str a-txt))
      (loop [urls (str/split-lines (slurp a-txt))]
        (when-let [a-url (first urls)]
          (if (> (count a-url) 0) (url-dl a-url))
          (recur (rest urls)))))
    (if (= (fs/file-name a-txt) "bs.txt") (mod_violet_2_flat))
    (recur (rest args))))
