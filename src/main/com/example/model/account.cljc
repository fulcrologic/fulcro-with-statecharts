(ns com.example.model.account
  (:require
    #?@(:clj  [[clojure.string :as str]
               [com.example.components.database-queries :as queries]
               [com.example.lib.pathom.wrappers :refer [defresolver defmutation]]
               [com.example.components.pathom-env :as penv :refer [current-db]]
               [com.fulcrologic.fulcro.server.api-middleware :as fmw]
               [com.fulcrologic.rad.attributes :as attr]
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

#?(:clj
   (defresolver account-invoices
     "Find the invoices for an account that is specified as a parameter in the query."
     [env _]
     {::pc/output [{:account/invoices [:invoice/id]}]
      :check      (constantly true)}
     {:account/invoices (queries/get-customer-invoices env (:query-params env))}))


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

#?(:clj
   (defmutation login [env {:account/keys [email password]}]
     {:check      (constantly true)
      ::pc/output [:account/email :session/ok?]}
     (let [fail {:account/email email
                 :session/ok?   false}]
       (if (and email password)
         (let [db               (penv/current-db env)
               normalized-email (-> email (str/trim) (str/lower-case))
               {:password/keys [salt hashed-value iterations] :as actual-account} (d/pull db [:account/email :password/hashed-value :password/salt :password/iterations] [:account/email normalized-email])
               incoming-hash    (log/spy :info (attr/encrypt password salt iterations))]
           (if (= incoming-hash (log/spy :info hashed-value))
             (let [session {:account/email normalized-email
                            :session/ok?   true}]
               (fmw/augment-response session (fn [resp] (assoc resp :session session))))
             fail))
         fail)))
   :cljs
   (m/declare-mutation login `login))

#?(:clj
   (defmutation check-session [env _]
     {:check      (constantly true)
      ::pc/output [:account/email :session/ok?]}
     (let [session (log/spy :info (penv/session env))]
       (if (:session/ok? session)
         session
         {:session/ok? false})))
   :cljs
   (m/declare-mutation check-session `check-session))

#?(:clj
   (defmutation logout [env _]
     {:check      (constantly true)
      ::pc/output [:account/email :session/ok?]}
     (fmw/augment-response {:session/ok? false} (fn [resp] (assoc resp :session {:session/ok?   false
                                                                                 :account/email nil}))))
   :cljs
   (m/declare-mutation logout `logout))
