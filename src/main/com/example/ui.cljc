(ns com.example.ui
  (:require
    #?@(:cljs [[com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
               [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-menu :refer [ui-dropdown-menu]]
               [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-item :refer [ui-dropdown-item]]
               [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
               [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
               [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]])
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.example.ui.account-forms :refer [AccountForm AccountList]]
    [com.example.ui.invoice-forms :refer [InvoiceForm InvoiceList AccountInvoices]]
    [com.example.ui.item-forms :refer [ItemForm InventoryReport]]
    [com.example.ui.master-detail :as mdetail]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.rad-integration :as ri]
    [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
    [taoensso.timbre :as log]))

(defsc LandingPage [this props]
  {:query         [:ui/open?]
   :ident         (fn [] [:component/id ::LandingPage])
   :initial-state {}}
  (dom/div "Welcome"))

(defsc Root [this {::app/keys [active-remotes]}]
  {:query         [::app/active-remotes (scf/statechart-session-ident uir/session-id)]
   :initial-state {}}
  (let [config           (scf/current-configuration this uir/session-id)
        logged-in?       (contains? config :state/logged-in)
        routing-blocked? (uir/route-denied? this)
        busy?            (seq active-remotes)]
    (dom/div
      #?(:cljs (ui-modal {:open routing-blocked?}
                 (ui-modal-content {}
                   "Routing away from this form will lose your unsaved changes. Are you sure?")
                 (ui-modal-actions {}
                   (dom/button :.ui.negative.button {:onClick (fn [] (uir/abandon-route-change! this))} "No")
                   (dom/button :.ui.positive.button {:onClick (fn [] (uir/force-continue-routing! this))} "Yes"))))
      (div :.ui.top.menu
        (div :.ui.item "Demo")
        (when logged-in?
          #?(:cljs
             (comp/fragment
               (ui-dropdown {:className "item" :text "Account"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (uir/route-to! this AccountList {}))} "View All")
                   (ui-dropdown-item {:onClick (fn [] (ri/create! this AccountForm))} "New")))
               (ui-dropdown {:className "item" :text "Inventory"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (uir/route-to! this InventoryReport {}))} "View All")
                   (ui-dropdown-item {:onClick (fn [] (ri/create! this ItemForm))} "New")))
               (ui-dropdown {:className "item" :text "Invoices"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (uir/route-to! this InvoiceList {}))} "View All")
                   (ui-dropdown-item {:onClick (fn [] (ri/create! this InvoiceForm))} "New")
                   (ui-dropdown-item {:onClick (fn [] (uir/route-to! this AccountInvoices {:account/id (new-uuid 101)}))} "Invoices for Account 101")))
               (ui-dropdown {:className "item" :text "Reports"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (uir/route-to! this mdetail/AccountList {}))} "Master Detail"))))))

        (div :.right.menu
          (div :.item
            (div :.ui.tiny.loader {:classes [(when busy? "active")]})
            ent/nbsp ent/nbsp ent/nbsp ent/nbsp)
          (if logged-in?
            (comp/fragment
              (div :.ui.item
                (str "Logged in as " #_username))
              (div :.ui.item
                (dom/button :.ui.button {:onClick (fn [] (scf/send! this uir/session-id :event/logout))}
                  "Logout")))
            (div :.ui.item
              (dom/button :.ui.primary.button #_{:onClick #(auth/authenticate! this :local nil)}
                "Login")))))
      (div :.ui.container.segment
        (uir/ui-current-subroute this comp/factory)))))
