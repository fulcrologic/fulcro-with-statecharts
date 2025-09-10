(ns com.example.client
  (:require
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
    [com.fulcrologic.fulcro.algorithms.tx-processing.batched-processing :as btxn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [button div h2 h3 h4]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.statecharts :as sc]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :as ele :refer [entry-fn exit-fn on-entry on-exit parallel script script-fn state transition]]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
    [com.fulcrologic.statecharts.integration.fulcro.rad-integration :as ri]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes-options :as ro]
    [com.example.ui :refer [Root LandingPage]]
    [com.example.ui.login-dialog :refer [LoginForm]]
    [com.example.model.account :as m.account]
    [fulcro.inspect.tool :as it]
    [taoensso.timbre :as log]))

(defonce app (-> (rad-app/fulcro-rad-app {})
               (with-react18)
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
          (ri/report-state {:route/target `com.example.ui.account-forms/AccountList
                            :route/path   ["accounts"]})
          (ri/report-state {:route/target `com.example.ui.master-detail/AccountList
                            :route/path   ["master-detail"]})
          (ri/report-state {:route/target `com.example.ui.item-forms/InventoryReport
                            :route/path   ["items"]})
          (ri/report-state {:route/target `com.example.ui.invoice-forms/InvoiceList
                            :route/path   ["invoices"]})
          (ri/report-state {:route/target      `com.example.ui.invoice-forms/AccountInvoices
                            :report/param-keys [:account/id]
                            :route/path        ["account-invoices"]})
          (ri/form-state {:route/target `com.example.ui.item-forms/ItemForm
                          :route/path   ["item"]})
          (ri/form-state {:route/target `com.example.ui.invoice-forms/InvoiceForm
                          :route/path   ["invoice"]})
          (ri/form-state {:route/target `com.example.ui.account-forms/AccountForm
                          :route/path   ["account"]}))))))

(defn refresh []
  ;; hot code reload of installed controls
  (log/info "Reinstalling controls")
  (setup-RAD app)
  (uir/update-chart! app application-chart)
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
  (uir/start-routing! app application-chart)
  (app/mount! app Root "app" {:initialize-state? false}))
