(ns tad.web.handler
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [hiccup2.core :as html]
            [sci.core :as sci]
            [selmer.parser :as sp]
            [cheshire.core :as ches]
            [tad.web.store :refer [st-get st-put! st-put st-delete]]
            [taoensso.timbre :as tmbr]
            )
  (:import [java.net URLDecoder URLEncoder]))

;; A simple mime type utility from https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/util/mime_type.clj
(def ^{:doc "A map of file extensions to mime-types."}
  default-mime-types
  {"7z"       "application/x-7z-compressed"
   "aac"      "audio/aac"
   "ai"       "application/postscript"
   "appcache" "text/cache-manifest"
   "asc"      "text/plain"
   "atom"     "application/atom+xml"
   "avi"      "video/x-msvideo"
   "bin"      "application/octet-stream"
   "bmp"      "image/bmp"
   "bz2"      "application/x-bzip"
   "class"    "application/octet-stream"
   "cer"      "application/pkix-cert"
   "crl"      "application/pkix-crl"
   "crt"      "application/x-x509-ca-cert"
   "css"      "text/css"
   "csv"      "text/csv"
   "deb"      "application/x-deb"
   "dart"     "application/dart"
   "dll"      "application/octet-stream"
   "dmg"      "application/octet-stream"
   "dms"      "application/octet-stream"
   "doc"      "application/msword"
   "dvi"      "application/x-dvi"
   "edn"      "application/edn"
   "eot"      "application/vnd.ms-fontobject"
   "eps"      "application/postscript"
   "etx"      "text/x-setext"
   "exe"      "application/octet-stream"
   "flv"      "video/x-flv"
   "flac"     "audio/flac"
   "gif"      "image/gif"
   "gz"       "application/gzip"
   "htm"      "text/html"
   "html"     "text/html"
   "ico"      "image/x-icon"
   "iso"      "application/x-iso9660-image"
   "jar"      "application/java-archive"
   "jpe"      "image/jpeg"
   "jpeg"     "image/jpeg"
   "jpg"      "image/jpeg"
   "js"       "text/javascript"
   "json"     "application/json"
   "lha"      "application/octet-stream"
   "lzh"      "application/octet-stream"
   "mov"      "video/quicktime"
   "m3u8"     "application/x-mpegurl"
   "m4v"      "video/mp4"
   "mjs"      "text/javascript"
   "mp3"      "audio/mpeg"
   "mp4"      "video/mp4"
   "mpd"      "application/dash+xml"
   "mpe"      "video/mpeg"
   "mpeg"     "video/mpeg"
   "mpg"      "video/mpeg"
   "oga"      "audio/ogg"
   "ogg"      "audio/ogg"
   "ogv"      "video/ogg"
   "pbm"      "image/x-portable-bitmap"
   "pdf"      "application/pdf"
   "pgm"      "image/x-portable-graymap"
   "png"      "image/png"
   "pnm"      "image/x-portable-anymap"
   "ppm"      "image/x-portable-pixmap"
   "ppt"      "application/vnd.ms-powerpoint"
   "ps"       "application/postscript"
   "qt"       "video/quicktime"
   "rar"      "application/x-rar-compressed"
   "ras"      "image/x-cmu-raster"
   "rb"       "text/plain"
   "rd"       "text/plain"
   "rss"      "application/rss+xml"
   "rtf"      "application/rtf"
   "sgm"      "text/sgml"
   "sgml"     "text/sgml"
   "svg"      "image/svg+xml"
   "swf"      "application/x-shockwave-flash"
   "tar"      "application/x-tar"
   "tif"      "image/tiff"
   "tiff"     "image/tiff"
   "ts"       "video/mp2t"
   "ttf"      "font/ttf"
   "txt"      "text/plain"
   "wasm"     "application/wasm"
   "webm"     "video/webm"
   "wmv"      "video/x-ms-wmv"
   "woff"     "font/woff"
   "woff2"    "font/woff2"
   "xbm"      "image/x-xbitmap"
   "xls"      "application/vnd.ms-excel"
   "xml"      "text/xml"
   "xpm"      "image/x-xpixmap"
   "xwd"      "image/x-xwindowdump"
   "zip"      "application/zip"})

(defn- filename-ext
  "Returns the file extension of a filename or filepath."
  [filename]
  (when-let [ext (second (re-find #"\.([^./\\]+)$" filename))]
    (str/lower-case ext)))

;; https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/util/mime_type.clj
(defn- ext-mime-type
  "Get the mimetype from the filename extension. Takes an optional map of
  extensions to mimetypes that overrides values in the default-mime-types map."
  ([filename]
   (ext-mime-type filename {}))
  ([filename mime-types]
   (let [mime-types (merge default-mime-types mime-types)]
     (mime-types (filename-ext filename)))))

(defn- file-link
  "Get HTML link for a file/directory in the given dir."
  [uri dir f]
  (let [rel-path (fs/relativize dir f)
        ending (if (fs/directory? f) "/" "")
        names (seq rel-path)
        enc-names (map #(URLEncoder/encode (str %)) names)]
    [:a {:href (str uri (str/join "/" enc-names) ending)}
     (str rel-path ending)]))

(defn- index [uri dir f]
  (let [files (map #(file-link uri dir %)
                   (fs/list-dir f))]
    {:body (-> [:html
                [:head
                 [:meta {:charset "UTF-8"}]
                 [:title (str "Index of `" f "`")]]
                [:body
                 [:h1 "Index of " [:code (str f)]]
                 [:ul
                  (for [child files]
                    [:li child])]
                 [:hr]
                 [:footer
                  {:style {"text-align" "center"}}
                  "server by tad.web.svc"
                  [:br]
                  "[derived from http-server.clj]"]]]
               html/html
               str)}))

(defn- body
  ([path]
   (body path {}))
  ([path headers]
   {:headers (merge {"Content-Type" (ext-mime-type (fs/file-name path))} headers)
    :body (fs/file path)}))

(defn- with-ext [path ext]
  (fs/path (fs/parent path) (str (fs/file-name path) ext)))

(defn sci-exec-ns
	[my-ns] (update-vals (ns-publics my-ns) #(sci/copy-var* % (sci/create-ns my-ns))))

(defn sci-exec-sci
  [rel_dir kvs web_req path]
  (let [h2-ns (sci-exec-ns 'hiccup2.core)
	hu-ns (sci-exec-ns 'hiccup.util)
	hc-ns (sci-exec-ns 'hiccup.compiler)
        dp-ns (sci-exec-ns 'babashka.deps)
	pr-ns (sci-exec-ns 'babashka.process)
	fs-ns (sci-exec-ns 'babashka.fs)
        cp-ns (sci-exec-ns 'babashka.classpath)
	sp-ns (sci-exec-ns 'selmer.parser)
	cc-ns (sci-exec-ns 'cheshire.core)
        st-ns (sci-exec-ns 'tad.web.store)
        file (.getAbsolutePath path)
        source (slurp path)
	opts {:bindings {'kvs kvs 'req web_req 'rel_dir rel_dir
                         'load-file load-file
                         ;;'st-get st-get
                         ;;'st-put st-put 'st-put! st-put!
                         ;;'st-delete st-delete
                         }
              :classes {:allow :all}}
	ctx (sci/init
             (merge opts 
		    {:namespaces 
		     {'html h2-ns 'hiccup.util hu-ns 'hiccup.compiler hc-ns
		      'deps dp-ns 'proc pr-ns 'fs fs-ns 'sp sp-ns
                      'ches cc-ns 'twst st-ns 'cp cp-ns
                      ;;'babashka.impl.classpath cp-ns
		      'sci {'file (.getAbsolutePath path)}}}))]
    ;;		(sci/with-bindings
    ;;			{sci/ns @sci/ns
    ;;			 sci/file (.getAbsolutePath path)
    ;;			 sci/out *out*}
    (sci/eval-string* ctx source)
    ;;			 )
    ))

(defn exec-by-load-file
  [rel_dir kvs web_req path]
  ;;(println "path::" path)
  (binding []
    (intern 'user 'rel_dir rel_dir)
    (intern 'user 'kvs kvs)
    (intern 'user 'req web_req)
    (intern 'user 'path path)
    (load-file path)
    )
  )

(defn response-fs [f rel_dir {:keys [uri] :as web_req} {:keys [idx kvs] :as opts}]
  (let [headers {}
        index-file (fs/path f "index.html")
        ;;_ (println "kvs:" kvs)
        res (cond
              (and (fs/directory? f) (fs/readable? index-file))
              (body index-file)

              (and (fs/directory? f) idx)
              (index uri f f)

              (or (re-matches #"_.+\.(bb|clj|cljc|cljs)" (fs/file-name f))
                    (re-matches #"^/c/bb/.+(|\.)(|bb|clj|cljc|cljs)" uri))
	      (exec-by-load-file rel_dir kvs web_req (fs/file f))

              (fs/readable? f)
              (body f)

              (and (nil? (fs/extension f)) (fs/readable? (with-ext f ".html")))
              (body (with-ext f ".html") headers)

              :else
              {:status 404 :body (str "Not found `" (fs/file-name f) "` in " rel_dir)}
              )
        ]
    res
    ))

;; ig definitions ===================================================

(defn real_file_with_uri [dir uri]
  (fs/path dir (str/replace-first (URLDecoder/decode uri) #"^/" "")))

(defn real_file_with_file [dir file]
  (fs/path dir file))

(defmethod ig/init-key :tad.web.handle/pwd [_ {:keys [dir]}]
  (let [dir (or dir ".")
        dir (fs/file (-> (fs/path dir) (fs/file)
                         .getAbsolutePath (.replaceAll "/\\." "")))]
    (println "pwd:" dir)
    dir))

(defmethod ig/init-key :tad.web.handle/tmpl [_ {:keys [dir rel]}]
  (fn [{:keys [uri] :as web_req}]
    (let
        [p_file (get-in web_req [:params :file])
         rel_dir (fs/path dir (or rel ""))
         f (real_file_with_file rel_dir p_file)
         matched (and (re-matches #".+\.html$" uri) (fs/exists? f))
         sp_dir (-> rel_dir (fs/file) .getAbsolutePath)
         sp_res (sp/set-resource-path! sp_dir)
         res (if matched
               {:body (sp/render-file p_file web_req)}
               {:status 404 :body (str "not found `" (fs/file-name f) "` in " rel_dir)}
               )]
      ;;(println "f:: " f)
      res)))

(defmethod ig/init-key :tad.web.handle/fs [_ {:keys [dir rel idx kvs]}]
  (fn [{:keys [uri] :as web_req}]
    (let
        [p_file (get-in web_req [:params :file])
         idx (if (nil? idx) false idx)
         rel_dir (fs/path dir (or rel ""))
         f (real_file_with_file rel_dir p_file)
         res (response-fs f rel_dir web_req {:idx idx :kvs kvs})
         {ret-headers :headers} res
         headers {}
         _ (sp/cache-off!)
         ]
      ;;(println "f:: " f)
      (update res
              :headers (fn [response-headers]
                         (merge headers response-headers)))
      )))


(derive :tad/pwd :tad.web.handle/pwd)
