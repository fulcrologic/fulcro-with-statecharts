(ns com.example.model-rad.model
  (:require
    [com.example.model-rad.account :as account]
    [com.example.model-rad.item :as item]
    [com.example.model-rad.invoice :as invoice]
    [com.example.model-rad.line-item :as line-item]
    [com.example.model-rad.category :as category]
    [com.fulcrologic.rad.attributes :as attr]))

(def all-attributes (vec (concat
                           account/attributes
                           category/attributes
                           item/attributes
                           invoice/attributes
                           line-item/attributes)))

(def all-attribute-validator (attr/make-attribute-validator all-attributes))
