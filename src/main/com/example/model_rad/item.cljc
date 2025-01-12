(ns com.example.model-rad.item
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.wsscode.pathom.connect :as pc]
    #?(:clj [com.example.components.database-queries :as queries])
    [taoensso.timbre :as log]))

(defattr id :item/id :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr category :item/category :ref
  {ao/target      :category/id
   ao/cardinality :one
   ao/identities  #{:item/id}
   ao/schema      :production})

(defattr item-name :item/name :string
  {ao/identities #{:item/id}
   ao/schema     :production})

(defattr description :item/description :string
  {ao/identities #{:item/id}
   ao/schema     :production})

(defattr price :item/price :decimal
  {ao/identities #{:item/id}
   ao/schema     :production})

(defattr in-stock :item/in-stock :int
  {ao/identities #{:item/id}
   ao/schema     :production})

(defattr all-items :item/all-items :ref
  {ao/target    :item/id
   ::pc/output  [{:item/all-items [:item/id]}]
   ::pc/resolve (fn [{:keys [query-params] :as env} _]
                  #?(:clj
                     {:item/all-items (queries/get-all-items env (log/spy :info query-params))}))})

(def attributes [id item-name category description price in-stock all-items])
