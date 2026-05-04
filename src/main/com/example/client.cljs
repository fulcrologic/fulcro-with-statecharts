(ns com.example.client
  (:require
    [com.example.model.account :as m.account]
    [com.example.ui :refer [LandingPage Root]]
    [com.example.ui.login-dialog :refer [LoginForm]]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
    [com.fulcrologic.fulcro.algorithms.tx-processing.batched-processing :as btxn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :refer [entry-fn exit-fn on-entry on-exit parallel script script-fn state transition]]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
    [com.fulcrologic.statecharts.integration.fulcro.routing :as uir]
    [fulcro.inspect.tool :as it]
    [taoensso.timbre :as log]))

(defonce app (-> (rad-app/fulcro-rad-app {})
               (btxn/with-batched-reads)))

(defn setup-RAD [app]
  (rad-app/install-ui-controls! app sui/all-controls)
  (report/install-formatter! app :boolean :affirmation (fn [_ value] (if value "yes" "no"))))

(defsc Session [this props]
  {:query [:account/email
           :session/ok?]
   :ident (fn [] [:component/id :session])})

(defn session-ok? [env {:fulcro/keys [state-map]}]
  (get-in state-map [:component/id :session :session/ok?]))

(def session-management-nodes
  "Sections of a statechart can be put into a collection for embedding to make things clearer."
  [(transition {:event  :event/logout
                :target :com.example.ui.login-dialog/LoginForm}
     (script-fn [env data]
       [(ops/assign [:fulcro/state-map :component/id :session] {:session/ok? false})
        (fops/invoke-remote [(m.account/logout {})] {})]))
   (uir/rstate {:route/target `LoginForm
                :route/path   ["login"]}
     (transition {:event  :event/session-check
                  :cond   session-ok?
                  :target :com.example.ui.account-forms/AccountList})
     (transition {:event :event/login}
       (script-fn [env data _ params]
         [(fops/invoke-remote [(m.account/login params)]
            {:returning Session
             :ok-event  :event/session-check})]))
     (entry-fn []
       [(fops/invoke-remote [(m.account/check-session {})] {:returning Session
                                                            :ok-event  :event/session-check})]))])

(def application-chart
  (statechart {}
    (uir/routing-regions
      (uir/routes {:id           :region/routes
                   :routing/root Root}

        session-management-nodes

        (state {:id :state/logged-in}
          (uir/istate {:route/target  `com.example.ui.account-forms/AccountList
                       :route/segment "accounts"})
          (uir/istate {:route/target  `com.example.ui.master-detail/AccountList
                       :route/segment "master-detail"})
          (uir/istate {:route/target  `com.example.ui.item-forms/InventoryReport
                       :route/segment "items"})
          (uir/istate {:route/target  `com.example.ui.invoice-forms/InvoiceList
                       :route/segment "invoices"})
          (uir/istate {:route/target  `com.example.ui.invoice-forms/AccountInvoices
                       :route/segment "account-invoices"})
          (uir/istate {:route/target  `com.example.ui.item-forms/ItemForm
                       :route/segment "item"})
          (uir/istate {:route/target  `com.example.ui.invoice-forms/InvoiceForm
                       :route/segment "invoice"})
          (uir/istate {:route/target  `com.example.ui.account-forms/AccountForm
                       :route/segment "account"}))))))

(defn refresh []
  ;; hot code reload of installed controls
  (log/info "Reinstalling controls")
  (setup-RAD app)
  ;(uir/update-chart! app application-chart)
  (app/force-root-render! app))

(defn init []
  (it/add-fulcro-inspect! app)
  (log/merge-config! {:output-fn prefix-output-fn
                      :appenders {:console (console-appender)}})
  (log/info "Starting App")
  ;; default time zone (should be changed at login for given user)
  (datetime/set-timezone! "America/Los_Angeles")
  ;; Avoid startup async timing issues by pre-initializing things before mount
  (app/set-root! app Root {:initialize-state? true})
  (setup-RAD app)
  (scf/install-fulcro-statecharts! app)
  (uir/start! app application-chart)
  (app/mount! app Root "app" {:initialize-state? false}))
