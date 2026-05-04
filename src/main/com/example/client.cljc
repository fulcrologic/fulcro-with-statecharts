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
    [com.fulcrologic.fulcro.raw.application :as rapp]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.statecharts :as sc]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :as ele :refer [on-entry on-exit parallel script script-fn state transition entry-fn exit-fn]]
    [com.fulcrologic.statecharts.convenience :refer [on]]
    [com.fulcrologic.statecharts.environment :as senv]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fop]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes-options :as ro]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [fulcro.inspect.tool :as it]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]))


(pc/defresolver events-resolver [env _]
  {::pc/output [{:event/all [:event/id]}]}
  {:event/all (mapv
                (fn [i] {:event/id i})
                (range 4))})

(pc/defresolver event-resolver [env {:event/keys [id]}]
  {::pc/input  #{:event/id}
   ::pc/output [:event/name]}
  {:event/id   id
   :event/name (str "Thing" id)})

(pc/defresolver event-tickets [env _]
  {::pc/output [{:event/tickets [:ticket/id]}]}
  (let [{:event/keys [id]} (:query-params env)]
    (when id
      {:event/tickets (mapv
                        (fn [i] {:ticket/id i})
                        (range 4))})))

(pc/defresolver ticket-resolver [env {:ticket/keys [id]}]
  {::pc/input  #{:ticket/id}
   ::pc/output [:ticket/price
                :ticket/label]}
  {:ticket/id    id
   :ticket/label (str "Ticket" id)})

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

(defonce app (app/fulcro-app #?(:cljs {:remotes {:remote (msr/mock-http-server {:parser parser})}})))

(defsc Root [this props]
  {:query         [:ui/loading?]
   :initial-state {:ui/loading? false}}
  (div :.ui.grid
    (div :.eight.wide.column
      (div :.ui.items
        (div :.item {:classes []
                     :onClick (fn [] (uir/route-to! this `RouteA2))} "Landing Page")
        (div :.item {:classes []
                     :onClick (fn [] (uir/route-to! this `RouteA1))} "Events"))
      (div :.ui.segment
        (uir/ui-current-subroute this comp/factory)))))

(defsc LandingPage [this {:ui/keys [clicks] :as props}]
  {:query         [:ui/clicks]
   :initial-state {:ui/clicks 0}
   :ident         (fn [] [:component/id ::RouteA1])}
  (div :.ui.basic.container
    (h3 "Landing Page")
    (button {:onClick (fn [] (m/set-integer! this :ui/clicks :value (inc clicks)))}
      (str clicks))))

(defsc EventRow [this props]
  {:query [:event/id :event/name]
   :ident :event/id}
  (dom/li nil
    (str (:event/name props))))

(def ui-event-row (comp/factory EventRow))

(defsc Events [this {:event/keys [all]}]
  {:query         [{:event/all (comp/get-query EventRow)}]
   :ident         (fn [] [:component/id ::Events])
   :initial-state {:event/all []}}
  (div
    "Events"
    (when (vector? all)
      (dom/ul nil
        (mapv ui-event-row all)))))

(defsc TicketRow [this props]
  {:query [:ticket/id :ticket/label]
   :ident :ticket/id}
  (dom/li nil
    (str (:ticket/label props))))

(def ui-ticket-row (comp/factory TicketRow))

(defsc Tickets [this {:event/keys [tickets]}]
  {:query         [{:event/tickets (comp/get-query TicketRow)}]
   :ident         (fn [] [:component/id ::Tickets])
   :initial-state {:event/tickets []}}
  (div
    "Tickets"
    (when (vector? tickets)
      (dom/ul nil
        (mapv ui-ticket-row tickets)))))

(defsc EventDashboard [this props]
  {:query [:event/id :event/name]
   :ident :event/id}
  (div
    (div (:event/name props))
    (dom/button {} "View Tickets")
    (div nil
      (uir/ui-current-subroute this comp/factory))))

(defn route-path-prefix
  "Returns a statechart PREDICATE that will ONLY return true IF:

   * We are evaluating at the given routing-depth (root is 0)
   * The sub-path-to-consume is the (a vector of element) is the sub-path to consume.

   The sub-path-to-consume may contain strings and keywords. Keywords cause that element to be skipped, but strings must match
   the remaining path exactly.
  "
  [routing-depth sub-path-to-consume]
  (fn [env {::keys [path-remaining current-routing-depth]}]
    (let [elements (take (count sub-path-to-consume) path-remaining)
          pairs    (map vector sub-path-to-consume elements)]
      (and
        (or (nil? current-routing-depth) (= current-routing-depth routing-depth))
        (= (count elements) (count sub-path-to-consume))
        (every? (fn [[a b]]
                  (or (keyword? a) (keyword? b) (= a b))) pairs)))))

(defn consume-route-path
  "Returns a statechart operation that will increase the routing depth by one, and
   remove `route-pattern` elements from the remaining path. Generates a new route-params
   for any keyword-indicated values from the remaining path."
  [path-pattern]
  (fn [env {::keys [current-routing-depth
                    path-remaining]}]
    (let [new-route-params (enc/remove-keys string? (zipmap path-pattern path-remaining))]
      [(ops/assign ::current-routing-depth (inc current-routing-depth))
       (ops/assign ::path-remaining (drop n path-remaining))
       (ops/assign ::route-params new-route-params)])))

(comment
  (let [pred (route-path-prefix 0 ["foo" "bar"])]
    (pred {} {::path-remaining        ["foo" "bar" "baz"]
              ::current-routing-depth 1})
    ))

(defn establish-static-route [Parent Target]
  (on-entry {}
    (script {:expr
             (fn [{:fulcro/keys [app]} & _]
               (rc/replace-join! app Parent
                 (when (rc/has-ident? Parent) (rc/get-ident Parent {}))
                 :ui/current-route Target (comp/get-ident Target)))})))

(defn establish-dynamic-route [Parent parent-ident Target target-ident]
  (on-entry {}
    (script {:expr
             (fn [{:fulcro/keys [app]} & _]
               (rc/replace-join! app Parent
                 parent-ident
                 :ui/current-route Target target-ident))})))

(defn missing-route-params [& params]
  (on-entry {}
    (script {:expr
             (fn [_ {::keys [route-params]} & _]
               (not
                 (every?
                   #(contains? route-params %)
                   params)))})))

(def application-chart
  (statechart {}
    (ele/data-model {:expr {:fulcro/aliases {:error         [:actor/root :ui/error]
                                             :current-event [:actor/root :ui/current-event]}}})

    (state {:id :state/root}
      (transition {:event  :error.*
                   :target :route/landing-page})

      (on :route.to/landing-page :route/landing-page)
      (on :route.to/events :route/events)
      (on :route.to/event-dashboard :route/event-dashboard)
      (on :route.to/tickets :route/tickets)

      (state {:id :route/landing-page}
        (establish-static-route Root LandingPage))

      ;; No cascade, but has I/O
      (state {:id :route/events}
        (state {:id :route.events/loading}
          (entry-fn [env data]
            [(fop/load :event/all EventRow {:abort-id :event/all})])
          (exit-fn [{:fulcro/keys [app]} data]
            (rapp/abort! app :event/all))
          (transition {:event  :event.loading/ok
                       :target :route.events/complete})
          (transition {:event  :error.*
                       :target :route.event/complete}
            (script-fn [env data] [(fop/assoc-alias :error "Could not load events")])))
        (state {:id :route.events/complete}
          (establish-static-route Root Events)
          (on-entry {} (ele/raise {:event :event.routing/complete}))))

      ;; Dynamic cascade with I/O
      (state {:id :route/event}
        (state {:id :route.event/loading}
          (transition {:target :route/landing-page
                       :cond   (missing-route-params :event/id)}
            (script-fn [_ _]
              [(fop/assoc-alias :error "Missing route parameter :event/id")]))
          (entry-fn [env data]
            (let [id (some-> data :route-params :event/id (parse-long))]
              [(ops/assign [:application/current-event] [:event/id id])
               (fop/load [:event/id id] EventDashboard {:abort-id :current-event-load})]))
          (exit-fn [{:fulcro/keys [app]} data]
            (rapp/abort! app :current-event-load))
          (transition {:event  :event.loading/ok
                       :target :route.event/complete}
            (script-fn [env data]
              (let [id (some-> data :route-params :event/id (parse-long))]
                [(fop/assoc-alias :current-event [:event/id id])])))
          (transition {:event  :error.*
                       :target :route/events}
            (script-fn [env data] [(fop/assoc-alias :error "Could not load event")])))
        (state {:id :route.event/complete}
          (entry-fn [env {:keys [current-event]}]
            (establish-dynamic-route Root nil EventDashboard current-event))

          (transition {:cond   (route-path-prefix 1 ["tickets"])
                       :target :route.event/tickets}
            (script {:expr (consume-route-path ["tickets"])}))

          (state {:id :route.event/menu})

          (state {:id :route.event/tickets}
            (state {:id :route.event.tickets/loading}
              (entry-fn [env {:keys [current-event]}] [(fop/load current-event TicketRow {:abort-id :current-load
                                                                                          :target   (conj current-event :event/tickets)})])
              (exit-fn [{:fulcro/keys [app]} & _] (rapp/abort! app :current-load))
              (transition {:event :event.loading/ok :target :route.event.tickets/complete}
                (script-fn [env data]
                  (let [id (some-> data :route-params :event/id (parse-long))]
                    [(fop/assoc-alias :current-event [:event/id id])])))
              (transition {:event  :error.*
                           :target :route/events}
                (script-fn [env data] [(fop/assoc-alias :error "Could not load tickets")])))
            (state {:id :route.event.tickets/complete}
              (script-fn [{:fulcro/keys [app]} {:keys [current-event]}]
                (rc/replace-join! app EventDashboard current-event :ui/current-route Tickets (comp/get-ident Tickets {}))))))))))

(def application-chart
  (statechart {}
    (ele/data-model {:expr {:fulcro/aliases {:error         [:actor/root :ui/error]
                                             :current-event [:actor/root :ui/current-event]}}})

    (state {:id :state/root}
      (transition {:event :event.routing/complete}
        (script {:expr (fn [& _]
                         (log/info "UPDATE URL"))}))

      (transition {:event  :error.*
                   :target :route/landing-page})

      (transition {:event  :route.to/path
                   :cond   (route-path-prefix 0 ["landing-page"])
                   :target :route/landing-page}
        (script {:expr (consume-route-path ["landing-page"])}))

      (transition {:event  :route.to/path
                   :cond   (route-path-prefix 0 ["events"])
                   :target :route/events}
        (script {:expr (consume-route-path ["events"])}))

      (transition {:event  :route.to/path
                   :cond   (route-path-prefix 0 ["event" :event/id])
                   :target :route/events}
        (script {:expr (consume-route-path ["event" :event/id])}))

      ;; No cascade. Simple static route
      (state {:id :route/landing-page}
        (establish-static-route Root LandingPage)
        (on-entry {} (ele/raise {:event :event.routing/complete})))

      ;; No cascade, but has I/O
      (state {:id :route/events}
        (state {:id :route.events/loading}
          (entry-fn [env data]
            [(fop/load :event/all EventRow {:abort-id :event/all})])
          (exit-fn [{:fulcro/keys [app]} data]
            (rapp/abort! app :event/all))
          (transition {:event  :event.loading/ok
                       :target :route.events/complete})
          (transition {:event  :error.*
                       :target :route.event/complete}
            (script-fn [env data] [(fop/assoc-alias :error "Could not load events")])))
        (state {:id :route.events/complete}
          (establish-static-route Root Events)
          (on-entry {} (ele/raise {:event :event.routing/complete}))))

      ;; Dynamic cascade with I/O
      (state {:id :route/event}
        (state {:id :route.event/loading}
          (transition {:target :route/landing-page
                       :cond   (missing-route-params :event/id)}
            (script-fn [_ _]
              [(fop/assoc-alias :error "Missing route parameter :event/id")]))
          (entry-fn [env data]
            (let [id (some-> data :route-params :event/id (parse-long))]
              [(ops/assign [:application/current-event] [:event/id id])
               (fop/load [:event/id id] EventDashboard {:abort-id :current-event-load})]))
          (exit-fn [{:fulcro/keys [app]} data]
            (rapp/abort! app :current-event-load))
          (transition {:event  :event.loading/ok
                       :target :route.event/complete}
            (script-fn [env data]
              (let [id (some-> data :route-params :event/id (parse-long))]
                [(fop/assoc-alias :current-event [:event/id id])])))
          (transition {:event  :error.*
                       :target :route/events}
            (script-fn [env data] [(fop/assoc-alias :error "Could not load event")])))
        (state {:id :route.event/complete}
          (entry-fn [env {:keys [current-event]}]
            (establish-dynamic-route Root nil EventDashboard current-event))

          (transition {:cond   (route-path-prefix 1 ["tickets"])
                       :target :route.event/tickets}
            (script {:expr (consume-route-path ["tickets"])}))

          (state {:id :route.event/menu})

          (state {:id :route.event/tickets}
            (state {:id :route.event.tickets/loading}
              (entry-fn [env {:keys [current-event]}] [(fop/load current-event TicketRow {:abort-id :current-load
                                                                                          :target   (conj current-event :event/tickets)})])
              (exit-fn [{:fulcro/keys [app]} & _] (rapp/abort! app :current-load))
              (transition {:event :event.loading/ok :target :route.event.tickets/complete}
                (script-fn [env data]
                  (let [id (some-> data :route-params :event/id (parse-long))]
                    [(fop/assoc-alias :current-event [:event/id id])])))
              (transition {:event  :error.*
                           :target :route/events}
                (script-fn [env data] [(fop/assoc-alias :error "Could not load tickets")])))
            (state {:id :route.event.tickets/complete}
              (script-fn [{:fulcro/keys [app]} {:keys [current-event]}]
                (rc/replace-join! app EventDashboard current-event :ui/current-route Tickets (comp/get-ident Tickets {}))))))))))

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
