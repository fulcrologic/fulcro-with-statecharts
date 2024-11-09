(ns com.example.lib.pathom.pathom-registry
  (:require
    [taoensso.timbre :as log]
    [com.wsscode.pathom.connect :as pc]))

(defonce pathom-registry (atom {}))

(defn register! [{::pc/keys [sym] :as resolver}]
  (log/debug "Registering resolver" sym)
  (swap! pathom-registry assoc sym resolver))

