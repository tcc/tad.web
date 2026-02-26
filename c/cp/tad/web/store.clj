(ns tad.web.store)

(defn now-ms []
  (System/currentTimeMillis))

(deftype TimeoutStore [store def_ttl])

(defn st-get
  [this k]
  (let [v (get @(.store this) k)
          {:keys [value tad-ttl]} v]
      (if (nil? tad-ttl)
        v
        (when (and value (> tad-ttl (now-ms)))
          value))))

(defn st-put
  ([this k v ttl]
    (if (= ttl 0)
      (swap! (.store this) assoc k v)
      (let [expiry (+ (now-ms) ttl)]
        (swap! (.store this) assoc k {:value v :tad-ttl expiry})
        )))
  ([this k v]
   (st-put this k v (.def_ttl this))))

(defn st-put!
  [this k v]
   (if this (st-put this k v 0)))

(defn st-delete
  [this k]
    (if this (swap! (.store this) dissoc k)))

(ns-unmap *ns* '->TimeoutStore)

(defn timeout-store
  ([] (timeout-store (atom {}) 10000))
  ([store ttl]
   (TimeoutStore. store ttl)))

