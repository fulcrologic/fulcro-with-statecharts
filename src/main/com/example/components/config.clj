(ns com.example.components.config
  (:require
    [com.example.lib.logging :as logging]
    [com.fulcrologic.fulcro.server.config :as fserver]
    [mount.core :refer [args defstate]]
    [taoensso.timbre :as log]))

(defstate config
  "The overrides option in args is for overriding
   configuration in tests."
  :start (let [{:keys [config overrides]
                :or   {config "config/dev.edn"}} (args)
               loaded-config (merge (fserver/load-config! {:config-path config}) overrides)]
           (log/info "Loading config" config)
           (logging/configure-logging! loaded-config)
           loaded-config))
