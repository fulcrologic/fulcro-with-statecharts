(ns com.example.model.item
  (:require
    [com.example.lib.pathom.wrappers :refer [defresolver]]
    [com.wsscode.pathom.connect :as pc]
    [taoensso.timbre :as log]))

#?(:clj
   (defresolver item-category-resolver [{:keys [parser] :as env} {:item/keys [id]}]
     {::pc/input  #{:item/id}
      ::pc/output [:category/id :category/label]
      :check (constantly true)}
     (let [result (parser env [{[:item/id id] [{:item/category [:category/id :category/label]}]}])]
       (get-in (log/spy :info result) [[:item/id id] :item/category]))))
