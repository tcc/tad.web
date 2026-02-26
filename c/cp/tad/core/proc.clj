(ns tad.core.proc
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [babashka.process :as bp]
            [cheshire.core :as ches]
            [clojure.set :refer [difference] :rename {difference dff}]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as tmbr]))

(defn writer-chan
  ([out]
   (writer-chan (async/chan 10) out))
  ([ch out]
   (async/thread
     (try
       (with-open [wrt (io/writer out)]
         (loop []
	   (when-let [v (async/<!! ch)]
             (cond
               (coll? v) (.write wrt (ches/generate-string v))
               (string? v) (.write wrt v)
               :else (.write wrt (str v)))
	     (.flush wrt)
	     (recur)
             )))
       (catch Exception e (println (str "(E)writer-chan:" e)))))
   ch))

(defn reader-chan0
  ([in]
   (reader-chan0 (async/chan 10) in))
  ([ch in]
   (async/thread
     (try 
       (with-open [rdr (io/reader in)]
         (loop []
	   (when-let [line (.readLine rdr)]
             (println "line: " line)
	     (async/>!! ch line)
	     (recur)
             )))
       (catch Exception e (println (str "(E)reader-chan0:" e)))))
   ch))

(defn u8n [x]
  (cond
    (nil? x) 0
    (<= 0 x 127) 1
    (<= -62 x -33) 2
    (<= -32 x -17) 3
    (<= -16 x -12) 4
    :else 0))

(defn rebuf [buf buf_bgn x n]
  (doseq [xi (range n)]
    (aset-byte buf (+ buf_bgn xi) (get x xi))))

(defn debuf [buf n]
  (let [t (count (get (group-by #(= % 0) buf) false))]
    (loop [xs (range (- t n) t)
           result []]
      (if (and (> (count xs) 0) (> t n))
        (let [x (first xs)
              d (get buf x)
              _ (aset-byte buf x 0)]
          (recur (rest xs) (conj result d)))
        result)
      )))


(defn xc [x_]
  (loop [x x_
         rb 0
         ur 0]
    (let [n (count x)
          ;;_ (println "x: " (vec x) " n: " n )
          u (u8n (first x))
          ;;_ (println "\t u: " u )
          r (- n u)]
      (if (< 0 n)
        (if (= u 0)
          (recur (subvec x 1) (+ rb 1) ur)
          (if (<= u n)
            (recur (subvec x u) (+ rb u) ur)
            (recur [] rb (+ ur n))
            ))
        [rb ur]))))

(defn reader-chan
  ([in]
   (reader-chan (async/chan 10) in))
  ([ch in]
   (async/thread
     (let [BUF_SZ 1024
           buffer (byte-array BUF_SZ)]
       (try
         (with-open [pin (java.io.PushbackInputStream. in 3)]
           (loop []
	     (when-let [c (.read pin)]
             (let [_      (.unread pin c)
                   size   (min BUF_SZ (.available pin))
                   _      (java.util.Arrays/fill buffer (byte 0))
                   _      (.read pin buffer 0 size)
                   e_buf  (if (= size BUF_SZ) (debuf buffer 3))
                   [rb ur] (xc e_buf)
                   _      (if (> rb 0) (rebuf buffer (- size 3) e_buf rb))
                   _      (if (> ur 0) (doseq [i (range 3 rb -1)]
                                         (.unread pin
                                                  (get e_buf (dec i)))))
                   data   (String.
                           (if (= (- size ur) BUF_SZ)
                             buffer
                             (byte-array
                              (take-while #(-> % (= 0) not) buffer)))
                           "utf-8")
                   ;;_ (println "data: " data)
                   ;;_ (println "size: " size)
                   ;;_ (println "data# " (.length data))
                   ]
	     (async/>!! ch data)
	     (recur)
             ))))
       (catch Exception e (println (str "(E)reader-chan:" e))))))
   ch))

(defn process-chan
  [cmd]
  (let [proc (bp/process cmd)
        {:keys [in out err]} proc
        cin (writer-chan in)
        cout (reader-chan out)
        cerr (reader-chan err)]    
    (merge proc {:cin cin :cout cout :cerr cerr})))

(defn proc-chan
  [cmd & opts]
  (let [;;_ (println "cmd: " cmd ", opts: " opts)
        {:keys [shutdown exit-fn]} opts
        opts {:shutdown shutdown :exit-fn exit-fn}
        proc (bp/process cmd opts)
        {:keys [in out err]} proc
        cin (writer-chan in)
        cout (reader-chan out)
        cerr (reader-chan err)]    
    (merge proc {:cin cin :cout cout :cerr cerr})))

(comment
  (require '[babashka.process :as bp])
  (require '[tad.core.process :as tp])
  (require '[clojure.core.async :as async])
  (def p (tp/process-chan "sh"))
  (bp/alive? p) ;; true
  )

(defonce proc-pool (atom {}))
(defonce proc-os (atom {})) ;; os-pid : pid of proc-pool
(defonce term-pool (atom {}))

(defn get-pool-id
  [pool]
  (let [ka (keys pool)
        nid (if (nil? ka) 1 (inc (apply max ka)))
        cid (if (some? ka)
              (first (apply sorted-set
                            (dff (set (range 1 nid))
                                 (set ka))))
              nil)]
    (if (nil? cid) nid cid)))

(s/def :tad/tid int?)
(s/def :tad/pid int?)
(s/def :tad/req string?)
(s/def :tad/in string?)
(s/def :tad/req-p (s/keys :req-un [:tad/tid :tad/pid] :opt-un [:tad/req :tad/in]))
(s/def :tad/req-t (s/keys :req-un [:tad/tid :tad/req (not :tad/pid)]))
(s/def :tad/req-r (s/keys :req-un [:tad/req (not :tad/tid) (not :tad/pid)]))

(defn h-p-req
  [m]
  (let [{:keys [tid pid req]} m
        t_val (get @term-pool tid)
        ;;_ (println "--- t_val: " t_val)
        p_val (get @proc-pool pid)
        ;;_ (println "--- p_val: " p_val)
        ]
    (println "--- req: " req)
    (cond
      (= req "kill") (let [;;_ (println "\tdestroy")
                           _ (bp/destroy p_val)
                           ;;_ (println "\tcheck")
                           _ (try (bp/check p_val)
                                  (catch Exception e "check ex!"))
                           ;;_ (println "\talive?")
                           is_done (not (bp/alive? p_val))]
                       {:tid tid :pid pid :res (if is_done "ok" "err")})
      )))

(defn h-p-in
  [m]
  (let [{:keys [tid pid in]} m
        t_val (get @term-pool tid)
        _ (println "--- t_val: " t_val)
        p_val (get @proc-pool pid)
        _ (println "--- p_val: " p_val)
        cin (:cin p_val)
        ]
    (println "--- in: " in)
    (async/>!! cin in)
    ))

(defn p-shutdown-ch [ch proc]
  (println (str "[" (-> proc :proc .pid) "]") "shudown.")
  )

(defn p-exit-ch [ch os-proc]
  ;;(println ">>>exit: " os-proc)
  (let [os-pid (-> os-proc :proc .pid)
        pid (get @proc-os os-pid)
        ;;_ (println "\tos pid:: " os-pid " -> pid: " pid)
        proc (get @proc-pool pid)
        ;;_ (println "\tproc:: " proc)
        {:keys [tid pid cin cout cerr]} proc
        ;;_ (println "\ttid: " tid ", pid: " pid)
        t_val (get @term-pool tid)
        ;;_ (println "\tt_val:: " t_val)
        tout (:tout t_val)]
    (println (str "[" os-pid  "]") "close ch")
    (async/close! cin)
    (async/close! cout)
    (async/close! cerr)
    ;;(println "\tdissoc")
    (swap! proc-os dissoc os-pid)
    (swap! proc-pool dissoc pid)
    (when-not (nil? tid)
      (swap! term-pool update-in [tid :pids] disj pid)
      (async/>!! tout "===[event]===\n")
      (async/>!! tout {:tid tid :pid pid :event "exit" :code (:exit os-proc)})
      (async/>!! tout "\n"))
    ))

(defn h-t-cmd
  [m]
  (let [{:keys [tid req type]} m
        t_val (get @term-pool tid)
        _ (println "--- t_val: " t_val)
        tout (:tout t_val)
        ]
    (cond
      ;; (or (nil? type) (= type "cmd"))
      false (let [res        @(bp/process req {:out :string :err :out})
                  out-string (:out res)]
              {:tid tid :res "ok" :txt out-string :pid -1})
      ;;(= type "cmd-io")
      true  (let [cproc (async/chan 10)
                  p-sd (partial p-shutdown-ch cproc)
                  p-ex (partial p-exit-ch cproc)
                  proc (proc-chan req {:shutdown p-sd :exit-fn p-ex})
                  pid (get-pool-id @proc-pool)
                  os-pid (-> proc :proc .pid)
                  _ (swap! proc-os assoc os-pid pid)
                  val (assoc proc :tid tid :pid pid :cproc cproc)
                  _ (swap! proc-pool assoc pid val)
                  _ (swap! term-pool update-in [tid :pids] conj pid)
                  ]
              (async/thread
                (loop []
                  (when-let [v (async/<!! (:cout val))]
                    (println "v::" v)
                    (async/>!! tout "===[out]===\n")
                    (async/>!! tout {:tid tid :pid pid :out v})
                    (async/>!! tout "\n")
                    (recur))))
              {:tid tid :pid pid :res "ok"}
             )
      :else {:res "test"}
      )))

(defn h-r-init
  [m]
  (let [{:keys [tout]} m
        tid (get-pool-id @term-pool)
        val {:tid tid :tout tout :pids #{}}
        _ (swap! term-pool assoc tid val)]
    (-> val (assoc :res "ok"))))

(defn hnd-proc
  [m]
  (let [{:keys [req in]} m]
    (println "proc -> " m)
    (cond
      (some? req) (h-p-req m)
      (some? in) (h-p-in m)
      :else {:res "error" :txt (str "no proc cmd ")})
    ))

(defn hnd-term
  [m]
  (let [{:keys [req type]} m]
    (println "term -> " m)
    (cond
      (or (nil? type) (= type "cmd")) (h-t-cmd m)
      (= type "cmd-io") (h-t-cmd m)
      :else {:res "error" :txt (str "no req(" req "), type(" type ")")})
    ))

(defn hnd-root
  [m]
  (let [{:keys [req]} m]
    (println "root -> " m)
    ;;(println "\treq: " req)
    (cond
      (= req "init") (h-r-init m)
      :else {:res "error" :txt (str "no cmd(" req ")")})))

(defn hnd-else
  [m] (println "else -> " m))

(defn mux
  [m]
  (cond
    (s/valid? :tad/req-p m) hnd-proc
    (s/valid? :tad/req-t m) hnd-term
    (s/valid? :tad/req-r m) hnd-root
    :else hnd-else
    ))

(defn reader-json-chan
  ([in]
   (reader-json-chan (async/chan 100) in))
  ([ch in]
   (async/thread
     (with-open [rdr (io/reader in)]
       (while true
         (try
	   (when-let [json (ches/parse-stream rdr)]
             ;; process inbound json
             ;;(prn (str "json" json))
	     (async/>!! ch json)
	     ;;(recur)
             )
           (catch Exception e
             ;;(do
             (println (str "(E)reader-json-chan:" e))
             ;;  (if (.ready rdr)
             ;;    (recur)
             ;;    nil)
             ;;  )
             ))
         )
       )
     )
   ch
   ;;(Thread/sleep 5000)
   ;;(println "xxx")
   ))

(defn reader-json-chan-4-stdin
  ([in]
   (reader-json-chan-4-stdin (async/chan 100) in))
  ([ch in]
   (while true
     (try
       (when-let [json (ches/parse-stream *in* true)]
         ;;(prn (str "json" json))
	 (async/>!! ch json)
         )
       (catch Exception e
         (println (str "(E)reader-json-chan:" e))
         ))
     )
   ))

(defn default-hnd-fn
  [fci fco]
  (let [;; val (get @term-pool cid)
        ;; c_ain (val :in)
        ;; c_out (val :out)
        ]
    (async/thread
      (loop []
        (let [[v c] (async/alts!!
                     [(async/timeout 10000) ;;:timeout
                      fci] ;;([v] v)
                     ;; pending: vector of processes
                     )]
          (prn (str "v -> " v))
          ;;(prn (str "c -> " c))
          (if (nil? v) ;; :timeout)
            ;;(prn "timeout!!")
            (async/>!! fco "timeout!!")
            ;;(prn (str "somethine: " "..." v));;(async/<!! c_ain)))
            (async/>!! fco (ches/generate-string v)) ;; ping-pong
            )
          (if (nil? v) ;; :timeout)
            nil ;; close client, exists looping
            (recur))
          )))
    nil
    )
  )

(defn sim-init-base
  ([ain aout]
   (sim-init-base default-hnd-fn ain aout))
  ([hnd-fn ain aout]
   (let [;; tid (get-pool-id @term-pool)
         fc_in (async/chan 100)
         fc_out (writer-chan aout)
         ;; val {:cid cid :in c_ain :out c_aout :ain ain :aout aout}
         ;; _ (swap! term-pool assoc cid val)
         ]
     (hnd-fn fc_in fc_out)
     (reader-json-chan-4-stdin fc_in ain)
     )))



