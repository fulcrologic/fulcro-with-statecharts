(ns com.example.components.datomic
  (:require
    [com.example.components.config :refer [config]]
    [com.example.model-rad.model :refer [all-attributes]]
    [com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
    [datomic.client.api :as d]
    [mount.core :refer [defstate]]))

(defstate ^{:on-reload :noop} datomic-connections
  :start
  (datomic/start-databases all-attributes config))

(comment
  (let [c  (:main datomic-connections)
        db (d/db c)]
    (d/q '[:find (pull ?a [*])
           :where
           [?a :account/id]]
      db))

  )
