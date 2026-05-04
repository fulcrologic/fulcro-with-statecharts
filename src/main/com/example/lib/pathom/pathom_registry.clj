(ns com.example.lib.pathom.pathom-registry
  (:require
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]))

(defonce pathom-registry (atom {}))

(defn register! [{::pc/keys [sym] :as resolver}]
  (log/debug "Registering resolver" sym)
  (swap! pathom-registry assoc sym resolver))

