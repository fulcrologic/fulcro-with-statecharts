(ns com.example.components.pathom-env
  (:require [com.fulcrologic.rad.database-adapters.datomic-options :as do]))

(defn current-db [env] (deref (:production (do/databases env))))
(defn current-connection [env] (:production (do/connections env)))
(defn session [env] (get-in env [:ring/request :session]))
