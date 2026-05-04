(ns com.example.components.auto-resolvers
  (:require
    [com.example.model-rad.model :refer [all-attributes]]
    [com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
    [com.fulcrologic.rad.resolvers :as res]
    [mount.core :refer [defstate]]))

(defstate automatic-resolvers
  :start
  (vec
    (concat
      (res/generate-resolvers all-attributes)
      (datomic/generate-resolvers all-attributes :production))))
