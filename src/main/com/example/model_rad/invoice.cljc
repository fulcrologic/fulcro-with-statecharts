(ns com.example.model-rad.invoice
  (:require
    #?(:clj [com.example.components.database-queries :as queries])
    [cljc.java-time.local-date :as ld]
    [cljc.java-time.local-date-time :as ldt]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.rad.type-support.date-time :as dt]
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.wsscode.pathom.connect :as pc]
    [taoensso.encore :as enc]))

(defattr id :invoice/id :uuid
  {ao/identity? true
   ;:com.fulcrologic.rad.database-adapters.datomic/native-id? true
   ao/schema    :production})

(defattr date :invoice/date :instant
  {::form/field-style           :date-at-noon
   ::datetime/default-time-zone "America/Los_Angeles"
   ao/required? true
   ao/identities                #{:invoice/id}
   ao/schema                    :production})

(defattr line-items :invoice/line-items :ref
  {ao/target                                                       :line-item/id
   :com.fulcrologic.rad.database-adapters.sql/delete-referent?     true
   :com.fulcrologic.rad.database-adapters.datomic/attribute-schema {:db/isComponent true}
   ao/required?                                                    true
   ao/valid?                                                       (fn [v props k]
                                                                     (and
                                                                       (vector? v)
                                                                       (pos? (count v))))
   fo/validation-message                                           "You must have a least one line item."
   ao/cardinality                                                  :many
   ao/identities                                                   #{:invoice/id}
   ao/schema                                                       :production})

(defattr total :invoice/total :decimal
  {ao/identities      #{:invoice/id}
   ao/schema          :production
   ro/field-formatter (fn [report v] (math/numeric->currency-str v))
   ao/read-only?      true})

(defattr customer :invoice/customer :ref
  {ao/cardinality :one
   ao/target      :account/id
   ao/required?   true
   ao/identities  #{:invoice/id}
   ao/schema      :production})

(defattr all-invoices :invoice/all-invoices :ref
  {ao/target     :invoice/id
   ao/pc-output  [{:invoice/all-invoices [:invoice/id]}]
   ao/pc-resolve (fn [{:keys [query-params] :as env} _]
                   #?(:clj
                      {:invoice/all-invoices (queries/get-all-invoices env query-params)}))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statistics attributes.  Note that these are to-many, and are used by
;; reports that expect a given attribute to be grouped/filtered and possibly
;; aggregated values. Each of these statistics will output the same number of
;; items for given input groups, one for each group.
;;
;; These depend on the `groups` that are generated later in this file by the
;; `invoice-statistics` resolver.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defattr date-groups :invoice-statistics/date-groups :instant
  {ao/cardinality :many
   ao/style       :date
   ao/pc-input    #{:invoice-statistics/groups}
   ao/pc-output   [:invoice-statistics/date-groups]
   ao/pc-resolve  (fn [_ {:invoice-statistics/keys [groups]}]
                    {:invoice-statistics/date-groups (mapv :key groups)})})

(defattr gross-sales :invoice-statistics/gross-sales :decimal
  {ao/cardinality :many
   ao/style       :USD
   ao/pc-input    #{:invoice-statistics/groups}
   ao/pc-output   [:invoice-statistics/gross-sales]
   ao/pc-resolve  (fn [{:keys [query-params] :as env} {:invoice-statistics/keys [groups]}]
                    {:invoice-statistics/gross-sales (mapv (fn [{:keys [_ values]}]
                                                             (reduce
                                                               (fn [sales {:invoice/keys [total]}]
                                                                 (math/+ sales total))
                                                               (math/zero)
                                                               values)) groups)})})

(defattr items-sold :invoice-statistics/items-sold :int
  {ao/cardinality :many
   ao/pc-input    #{:invoice-statistics/groups}
   ao/pc-output   [:invoice-statistics/items-sold]
   ao/pc-resolve  (fn [{:keys [query-params] :as env} {:invoice-statistics/keys [groups]}]
                    {:invoice-statistics/items-sold (mapv (fn [{:keys [_ values]}]
                                                            (reduce
                                                              (fn [total {:invoice/keys [line-items]}]
                                                                (+ total (reduce
                                                                           (fn [m {:line-item/keys [quantity]}]
                                                                             (+ m quantity)) 0 line-items)))
                                                              0
                                                              values)) groups)})})

(def attributes [id date line-items customer all-invoices total date-groups gross-sales items-sold])

(comment
  (report/rotate-result
    {:invoice-statistics/date-groups ["1/1/2020" "2/1/2020" "3/1/2020" "4/1/2020"]
     :invoice-statistics/gross-sales [323M 313M 124M 884M]
     :invoice-statistics/items-sold  [10 11 5 42]}
    [date-groups gross-sales items-sold]))
