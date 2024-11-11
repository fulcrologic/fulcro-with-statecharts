(ns user
  (:require
    [clojure.spec.alpha :as s]
    [expound.alpha :as expound]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

