(ns com.example.client
  (:require
    [clojure.core.async :as async]
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [button div h2 h3 h4]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [button div h2 h3 h4]])
    [com.fulcrologic.fulcro.mutations :as m]
    #?(:cljs [com.fulcrologic.fulcro.networking.mock-server-remote :as msr])
    [com.fulcrologic.statecharts :as sc]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.elements :as ele :refer [on-entry on-exit parallel script script-fn state transition]]
    [com.fulcrologic.statecharts.environment :as senv]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes-options :as ro]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [fulcro.inspect.tool :as it]
    [taoensso.timbre :as log]))

(pc/defresolver thing-resolver [env {:thing/keys [id]}]
  {::pc/input  #{:thing/id}
   ::pc/output [:thing/value]}
  {:thing/id    id
   :thing/value (str "Thing" id)})

(defonce parser
  (let [parser (p/async-parser {::p/mutate  pc/mutate-async
                                ::p/env     {::p/reader [p/map-reader pc/async-reader2 pc/open-ident-reader]}
                                ::p/plugins [(pc/connect-plugin {::pc/register [thing-resolver]})]})]
    (fn [eql] (parser {} eql))))

(defonce app (app/fulcro-app {#_#_:remotes {:remote (msr/mock-http-server {:parser parser})}}))

(defsc Root [this props]
  {:query         [(scf/statechart-session-ident uir/session-id)]
   :initial-state {}}
  (let [route-denied? (uir/route-denied? this)]
    (div :.ui.container
      (h2 "Root")
      (dom/pre {}
        (str "ROOT QUERY"
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
          (dom/button {:onClick (fn [] (uir/force-continue-routing! this))} "Route anyway!")))
      (div :.ui.grid
        (div :.eight.wide.column
          (div :.ui.items
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA1))} "Goto A1")
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA2))} "Goto A2")
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA22))} "Goto A2.2")
            (div :.item {:classes []
                         :onClick (fn [] (uir/route-to! this `RouteA3))} "Goto A3"))
          (div :.ui.segment
            (uir/ui-current-subroute this comp/factory)))
        #_(div :.eight.wide.column
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

(defsc RouteA21 [this {:thing/keys [id value]}]
  {:query        [:thing/id
                  :thing/value
                  [::sc/session-id '_]]
   :ident        (fn [] [:component/id ::RouteA21])
   ro/statechart (statechart {}
                   (state {:id :top}
                     #_(on-entry {}
                         (script-fn [env & _]
                           (let [id       (tempid/tempid)
                                 my-ident [:thing/id id]
                                 thing    {:thing/id    id
                                           :thing/value 42}]
                             (merge/merge-component! app RouteA21 thing)
                             (scf/send! env (senv/parent-session-id env)
                               :event/child-ident-changed {:ident my-ident})
                             nil)))
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
   ro/initialize :once}
  (let [cconfig (uir/current-invocation-configuration this)]
    (div :.ui.basic.container {:key "21"}
      (h3 "Thing" (str id value))
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

(def application-chart
  (statechart {}
    (uir/routing-regions
      (uir/routes {:id           :region/routes
                   :routing/root Root}
        ;; MUST resolve ambiguity by putting an explicit path
        (parallel {}
          (uir/rstate {:route/target `RouteA1
                       :initial      :route/a}
            (uir/rstate {:id :route/a})
            (uir/rstate {:id :other}))
          (uir/rstate {:route/target `RouteA2
                       :route/path   ["c"]}
            (uir/rstate {:id         :route/c
                         :route/path "A"})
            (uir/istate {:route/target     `RouteA21
                         :route/path       ["B"]
                         :exit-target      ::RouteA1
                         :child-session-id ::route-a21})
            (uir/rstate {:route/target `RouteA22})))
        (uir/rstate {:parallel?    true
                     :route/target `RouteA3}
          (uir/rstate {:route/target `RouteA31})
          (uir/rstate {:route/target `RouteA32}))))))

(defn refresh []
  (log/info "Reinstalling controls")
  (uir/update-chart! app application-chart)
  (app/force-root-render! app))

(defn init []
  (log/info "Starting App")
  (it/add-fulcro-inspect! app)
  (app/set-root! app Root {:initialize-state? true})
  (scf/install-fulcro-statecharts! app)
  (uir/start-routing! app application-chart)
  (app/mount! app Root "app" {:initialize-state? false}))
