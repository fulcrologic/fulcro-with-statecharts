(ns com.example.model-rad.account
  (:refer-clojure :exclude [name])
  (:require
    [com.example.model.timezone :as timezone]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.database-adapters.datomic-options :as do]
    [com.fulcrologic.rad.form-options :as fo]))

(defattr id :account/id :uuid
  {ao/identity? true
   ;; NOTE: These are spelled out so we don't have to have either on classpath, which allows
   ;; independent experimentation. In a normal project you'd use ns aliasing.
   ao/schema    :production
   })

(defattr email :account/email :string
  {ao/identities       #{:account/id}
   ao/required?        true
   ao/schema           :production
   do/attribute-schema {:db/unique :db.unique/value}})

(defattr active? :account/active? :boolean
  {ao/identities    #{:account/id}
   ao/schema        :production
   fo/default-value true})

(defattr password :password/hashed-value :string
  {ao/required?       true
   ao/identities      #{:account/id}
   ::auth/permissions (fn [_] #{})
   ao/schema          :production})

(defattr password-salt :password/salt :string
  {ao/schema     :production
   ao/identities #{:account/id}
   ao/required?  true})

(defattr password-iterations :password/iterations :int
  {ao/identities      #{:account/id}
   ::auth/permissions (fn [_] #{})
   ao/schema          :production
   ao/required?       true})

(def account-roles {:account.role/superuser "Superuser"
                    :account.role/user      "Normal User"})

(defattr role :account/role :enum
  {ao/identities        #{:account/id}
   ao/enumerated-values (set (keys account-roles))
   ao/enumerated-labels account-roles
   ao/schema            :production})

(defattr name :account/name :string
  {fo/field-label "Name"
   ;::report/field-formatter (fn [v] (str "ATTR" v))
   ao/identities  #{:account/id}
   ;ao/valid?      (fn [v] (str/starts-with? v "Bruce"))
   ;::attr/validation-message                                 (fn [v] "Your name's not Bruce then??? How 'bout we just call you Bruce?")
   ao/schema      :production

   ao/required?   true})

(defattr time-zone-id :account/time-zone-id :enum
  {ao/required?         true
   ao/identities        #{:account/id}
   ao/schema            :production
   ao/enumerated-values (set (keys timezone/datomic-time-zones))
   ao/enumerated-labels timezone/datomic-time-zones
   fo/field-label       "Time Zone"
   ;; Enumerations with lots of values should use autocomplete instead of pushing all possible values to UI
   fo/field-style       :autocomplete
   fo/field-options     {:autocomplete/search-key    :autocomplete/time-zone-options
                         :autocomplete/debounce-ms   100
                         :autocomplete/minimum-input 1}})

(def attributes [id name role email password password-iterations password-salt active?
                 time-zone-id])
