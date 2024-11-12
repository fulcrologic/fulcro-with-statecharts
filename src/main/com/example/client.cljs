(ns com.example.client
  (:require
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div h2 h3 h4 button]]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
    [com.fulcrologic.statecharts.visualization.visualizer :as viz]
    [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as sync]
    [com.fulcrologic.fulcro.algorithms.tx-processing.batched-processing :as btxn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.rad.routing :as routing]
    [com.fulcrologic.rad.routing.history :as history]
    [com.fulcrologic.rad.routing.html5-history :as hist5 :refer [new-html5-history]]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
    [com.fulcrologic.statecharts :as sc]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :as ele :refer [on-entry parallel script state transition]]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes-options :as ro]
    [taoensso.timbre :as log]
    [taoensso.tufte :as tufte :refer [profile]]))

(defonce app (-> (rad-app/fulcro-rad-app {})
               (with-react18)
               (btxn/with-batched-reads)))

(defn setup-RAD [app]
  (rad-app/install-ui-controls! app sui/all-controls)
  (report/install-formatter! app :boolean :affirmation (fn [_ value] (if value "yes" "no"))))

(defsc Root [this {:ui/keys [visualizer]}]
  {:query         [(scf/statechart-session-ident uir/session-id)
                   {:ui/visualizer (comp/get-query viz/Visualizer)}]
   :initial-state {:ui/visualizer {:chart-id ::uir/chart}}}
  (let [route-denied? (uir/route-denied? this)]
    (div :.ui.container
      (h2 "Root")
      #_(dom/pre {}
          (str
            (with-out-str
              (pprint (comp/get-query this)))
            "\nConfig: "
            (with-out-str
              (pprint
                (scf/current-configuration this uir/session-id)))
            "\nActive leaves: "
            (uir/active-leaf-routes this)))
      (when route-denied?
        (div :.ui.error.message
          "The route was denied because it is busy! "
          (dom/a {:onClick (fn [] (uir/force-continue-routing! this))} "Route anyway!")))
      (div :.ui.grid
        (div :.eight.wide.column
          (div :.ui.items
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA1))} "Goto A1")
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA2))} "Goto A2")
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA3))} "Goto A3"))
          (div :.ui.segment
            (uir/ui-current-subroute this comp/factory)))
        (div :.eight.wide.column
          (viz/ui-visualizer visualizer {:session-id uir/session-id}))))))

(defsc RouteA1 [this {:ui/keys [clicks] :as props}]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   :ident         (fn [] [:component/id ::RouteA1])}
  (div :.ui.basic.container
    (h3 "Route A1 (leaf)")
    (button {:onClick (fn [] (m/set-integer! this :ui/clicks :value (inc clicks)))}
      (str clicks))))

(defsc RouteA2 [this props]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   :ident         (fn [] [:component/id ::RouteA2])}
  (div :.ui.basic.container
    (h3 "Route A2")
    (div :.ui.items
      (div :.item {:classes []
                   :onClick (fn [] (uir/route-to! this `RouteA21))} "Goto A21")
      (div :.item {:classes []
                   :onClick (fn [] (uir/route-to! this `RouteA22))} "Goto A22"))
    (uir/ui-current-subroute this comp/factory)))

(defsc RouteA21 [this {:ui/keys [clicks] :as props}]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   :ident         (fn [] [:component/id ::RouteA21])}
  (div :.ui.basic.container
    (h3 "Route A21")
    (button {:onClick (fn [] (m/set-integer! this :ui/clicks :value (inc clicks)))}
      (str clicks))))

(defsc RouteA22 [this {:ui/keys [clicks] :as props}]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   ro/busy?       (fn [& args] true)
   :ident         (fn [] [:component/id ::RouteA22])}
  (div :.ui.basic.container
    (h3 "Route A22")
    (button {:onClick (fn [] (m/set-integer! this :ui/clicks :value (inc clicks)))}
      (str clicks))))

(defsc RouteA3 [this {:ui/keys [clicks] :as props}]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   :ident         (fn [] [:component/id ::RouteA3])}
  (div :.ui.basic.container
    (h3 "Route A3")
    (div :.ui.grid
      (div :.eight.wide.column
        (uir/ui-parallel-route this `RouteA31 comp/factory))
      (div :.eight.wide.column
        (uir/ui-parallel-route this `RouteA32 comp/factory)))))

(defsc RouteA31 [this {:ui/keys [clicks] :as props}]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   :ident         (fn [] [:component/id ::RouteA31])}
  (div :.ui.basic.container
    (h3 "Route A31")
    (button {:onClick (fn [] (m/set-integer! this :ui/clicks :value (inc clicks)))}
      (str clicks))))

(defsc RouteA32 [this {:ui/keys [clicks] :as props}]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   :ident         (fn [] [:component/id ::RouteA32])}
  (div :.ui.basic.container
    (h3 "Route A32")
    (button {:onClick (fn [] (m/set-integer! this :ui/clicks :value (inc clicks)))}
      (str clicks))))

(def application-chart
  (statechart {}
    (uir/routing-regions
      (uir/routes {:id           :region/routes
                   :routing/root Root}
        (uir/rstate {:route/target `RouteA1})
        (uir/rstate {:route/target `RouteA2}
          (uir/rstate {:route/target `RouteA21})
          (uir/rstate {:route/target `RouteA22}))
        (uir/rstate {:parallel?    true
                     :route/target `RouteA3}
          (uir/rstate {:route/target `RouteA31})
          (uir/rstate {:route/target `RouteA32}))))))

(defn refresh []
  ;; hot code reload of installed controls
  (log/info "Reinstalling controls")
  (setup-RAD app)
  (uir/update-chart! app application-chart)
  (app/force-root-render! app))

(defn init []
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
