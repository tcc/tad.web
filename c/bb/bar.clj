;;(sp/set-resource-path! "/Users/tcc/wa/bb/c/tmpl")
;;{:headers {"Content-Type" "application/json"} :body "[1,2,3,4,5]"}
(comment
  (require '[cheshire.core :refer :all])
  ;;(prn (generate-string {:foo "bar" :baz 5}))
  (prn (parse-stream (clojure.java.io/reader *in*)))
  )

(comment
(def c_ch (chan 1))
(thread
  (loop []
    (let [[v c] (alts!! [c_ch co])]
      (if (= c c_ch)
        (println "done")
        (do
          (println "v=" v ", c=" c)
          (recur)
          )))))
  )

(def m0_1 {:req "init"})
;; {:tid 1 :res "ok"}

(def m1_1 {:tid 1 :req "ls"})
;; rx: {:tid 1 :pid 1 :res "ok"}
;; rx: {:tid 1 :pid 1 :out ... }
;; rx: {:tid 1 :pid 1 :ret 0} ;; exit code of ls

(def m2_1 {:tid 1 :req "cat -"})
;; rx: {:tid 1 :pid 2 :res "ok"}
(def m2_2 {:tid 1 :pid 2 :in "foo\n"})
;; tx: {:tid 1 :pid 2 :in "foo\n"}
;; rx: {:tid 1 :pid 2 :out foo\n"}
;; ...
(def m2_3 {:tid 1 :pid 2 :req "kill"})
;; tx: {:tid 1 :pid 2 :req "kill"}
;; rx: {:tid 1 :pid 2 :res "ok"}
;; rx: {:tid 1 :pid 2 :ret 0}

(defn hnd-proc
  [m] (println "proc -> " m))

(defn hnd-term
  [m] (println "term -> " m))

(defn hnd-norm
  [m] (println "norm -> " m))

(defn hnd-else
  [m] (println "else -> " m))

(defn mux
  [m]
  (cond
    (and (:pid m) (:tid m)) (hnd-proc m)
    (:tid m) (hnd-term m)
    (and (:req m) (not (:tid m)) (not (:pid m))) (hnd-norm m)
    :else (hnd-else m)
    ))
