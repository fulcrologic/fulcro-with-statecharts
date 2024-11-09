(ns com.example.model.account
  (:require
    #?@(:clj  [[com.example.components.database-queries :as queries]
               [com.example.lib.pathom.wrappers :refer [defresolver defmutation]]
               [com.example.components.pathom-env :as penv :refer [current-db]]
               [datomic.client.api :as d]]
        :cljs [[com.fulcrologic.fulcro.mutations :as m]])
    [com.fulcrologic.fulcro.raw.components :as rc]
    [taoensso.timbre :as log]
    [com.wsscode.pathom.connect :as pc]))

#?(:clj
   (defn get-all-accounts
     [env query-params]
     (if-let [db (current-db env)]
       (let [ids (if (:show-inactive? query-params)
                   (d/q '[:find ?uuid
                          :where
                          [?dbid :account/id ?uuid]] db)
                   (d/q '[:find ?uuid
                          :where
                          [?dbid :account/active? true]
                          [?dbid :account/id ?uuid]] db))]
         (mapv (fn [[id]] {:account/id id}) ids))
       (log/error "No database atom for production schema!"))))

#?(:clj
   (defresolver all-accounts [{:keys [query-params] :as env} inpu]
     {::pc/output [{:account/all-accounts [:account/id]}]
      :check      (constantly true)}
     {:account/all-accounts (get-all-accounts env query-params)}))

#_(defattr account-invoices :account/invoices :ref
    {ao/target     :account/id
     ao/pc-output  [{:account/invoices [:invoice/id]}]
     ao/pc-resolve (fn [{:keys [query-params] :as env} _]
                     #?(:clj
                        {:account/invoices (queries/get-customer-invoices env query-params)}))})




#?(:clj
   (defmutation set-account-active [env {:account/keys [id active?]}]
     {:check (constantly true)}
     (d/transact (penv/current-connection env) {:tx-data [[:db/add [:account/id id] :account/active? active?]]})
     {:account/id id})
   :cljs
   (m/defmutation set-account-active [{:account/keys [id active?]}]
     (remote [e]
       (-> e
         (m/returning (rc/nc [:account/id :account/active]))))))
