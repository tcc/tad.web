(sp/set-resource-path! "/Users/tcc/wa/bb/c/tmpl")

(prn req)

{:body (sp/render-file "home.html" nil)}
