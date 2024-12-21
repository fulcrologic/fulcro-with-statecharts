(ns com.example.client
  (:require
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.devtools.common.target :refer [ido]]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
    [com.fulcrologic.fulcro.algorithms.transit :as ft]
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
    [com.fulcrologic.statecharts.elements :as ele :refer [on-entry on-exit parallel script script-fn state transition]]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes-options :as ro]
    [fulcro.inspect.tool :as it]
    [taoensso.timbre :as log]))

(defonce app (-> (rad-app/fulcro-rad-app {})
               (with-react18)
               (btxn/with-batched-reads)))

(defn setup-RAD [app]
  (rad-app/install-ui-controls! app sui/all-controls)
  (report/install-formatter! app :boolean :affirmation (fn [_ value] (if value "yes" "no"))))

(defsc Root [this props]
  {:query         [(scf/statechart-session-ident uir/session-id)]
   :initial-state {}}
  (let [route-denied? (uir/route-denied? this)]
    (div :.ui.container
      (h2 "Root")
      (dom/pre {}
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
                         :onClick (fn [] (uir/route-to! this `RouteA22
                                           {:x 1 :y 2}))} "Goto A2.2")
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA3))} "Goto A3"))
          (div :.ui.segment
            (uir/ui-current-subroute this comp/factory)))))))

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
                   :onClick (fn [] (uir/route-to! this `RouteA22))} "Goto A22")
      (when (contains? (scf/current-configuration this uir/session-id) ::RouteA21)
        (dom/a :.item {:onClick (fn [] (scf/send! this ::route-a21 :event/swap))}
          "Send event to invoked chart from parent")))
    (uir/ui-current-subroute this comp/factory)))

(defsc RouteA21 [this props]
  {:query         [[::sc/session-id '_]]
   :initial-state {}
   ro/statechart  (statechart {}
                    (state {:id :top}
                      (on-exit {}
                        (script-fn [] (log/info "Exit top")))
                      (state {:id :red}
                        (on-exit {}
                          (script-fn [] (log/info "Exit red")))
                        (transition {:event  :event/swap
                                     :target :green}))
                      (state {:id :green}
                        (on-exit {}
                          (script-fn [] (log/info "Exit green")))
                        (transition {:event  :event/swap
                                     :target :exit})))
                    (ele/final {:id :exit}))
   ro/initialize  :once
   :ident         (fn [] [:component/id ::RouteA21])}
  (let [cconfig (uir/current-invocation-configuration this)]
    (div :.ui.basic.container {:key "21"}
      (h3 "Route A21")
      (button {:onClick (fn [] (uir/send-to-self! this :event/swap))}
        (str cconfig)))))

(defsc RouteA22 [this {:a22/keys [clicks] :as props}]
  {:query         [:a22/clicks]
   :initial-state {:a22/clicks 0}
   ro/busy?       (fn [& args] true)
   :ident         (fn [] [:component/id ::RouteA22])}
  (div :.ui.basic.container {:key "22"}
    (h3 "Route A22")
    (button {:onClick (fn [] (m/set-integer! this :a22/clicks :value (inc clicks)))}
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

;; URL Idea:
;; Each active state has route parameters that are necessary for entering the route. They can be supplied by
;; event data. When event data is used, then the data is written TO the URL and attached to that state ID.
;; When the routing system is interpreting a URL that is pushed, it looks at the target state, and
;; attempts to go there while also populating the event data with that URL data.
;; Each on-entry handler looks to establish its data. If it isn't present, an error event is raised, whose action is to
;; go to a well-known data-free state in the chart.
;; Paths are generally not used because a parallel fork of the states creates the need for multiple sub-paths for each
;; parallel child.
(def application-chart
  (statechart {}
    (uir/routing-regions
      (uir/routes {:id           :region/routes
                   :routing/root Root}
        (uir/rstate {:route/target `RouteA1
                     :route/path   "/a1"})
        (uir/rstate {:route/target `RouteA2}
          (uir/istate {:route/target     `RouteA21
                       :route/path       "/a2.1"
                       :exit-target      ::RouteA3
                       :child-session-id ::route-a21})
          (uir/rstate {:route/target `RouteA22
                       :route/params #{:x :y}
                       :route/path   "/a2.2"}))
        (uir/rstate {:parallel?    true
                     :route/path   "/a3"
                     ;; TASK: Validate that NO immediate children declare a route path
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
  (ido
    (it/add-fulcro-inspect! app))
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
