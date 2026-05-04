(ns com.fulcrologic.rad.rendering.semantic-ui.modals
  (:require
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.statechart.form :as sc-form]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]))

(defn use-statechart-form
  "Hook equivalent of `rad-hooks/use-form` that drives a RAD form via its
   *statechart* (not UISM). On mount, calls `sc-form/start-form!` with the
   given id; on unmount, calls `sc-form/abandon-form!`."
  [app-ish Form id save-complete-mutation cancel-mutation
   {:keys [save-mutation-params cancel-mutation-params]}]
  (let [container-id    (hooks/use-generated-id)
        app             (rc/any->app app-ish)
        id-key          (-> Form (rc/component-options) fo/id ao/qualified-key)
        form-ident      [id-key id]
        [container-component] (hooks/use-state
                                (fn []
                                  (rc/nc [{:ui/form (rc/get-query Form)}]
                                    {:initial-state (fn [_] {:ui/form {id-key id}})
                                     :ident         (fn [_ _] [::id container-id])})))
        container-props (hooks/use-component app container-component {:initialize     true
                                                                      :keep-existing? false})
        [form-factory] (hooks/use-state (fn [] (comp/computed-factory Form {:keyfn id-key})))]
    (hooks/use-lifecycle
      (fn []
        (sc-form/start-form! app id Form
          {:embedded? true
           :on-saved  [(save-complete-mutation (merge save-mutation-params {:ident form-ident}))]
           :on-cancel [(cancel-mutation (or cancel-mutation-params {:ident form-ident}))]}))
      (fn [] (sc-form/abandon-form! app form-ident)))
    {:form-factory (fn [props]
                     (when (and (map? props) (contains? props id-key))
                       (form-factory props)))
     :form-props   (get container-props :ui/form)}))

(defsc FormModal [this {:keys [id Form
                               save-mutation
                               cancel-mutation
                               save-params
                               cancel-params]}]
  {:use-hooks? true}
  (ui-modal {:open true}
    (ui-modal-content {}
      (let [[generated-id] (hooks/use-state (or id (tempid/tempid)))
            {:keys [form-factory form-props]} (use-statechart-form this Form generated-id
                                                save-mutation cancel-mutation
                                                {:save-mutation-params   save-params
                                                 :cancel-mutation-params cancel-params})]
        (form-factory form-props)))))

(def ui-form-modal
  "[{:keys [id Form save-mutation cancel-mutation save-params cancel-params]}]

    Render a form in a Semantic UI Modal.

    :Form - Required. The form to use for edit/create
    :save-mutation - Required. A *mutation* that will be transacted with the final ident if/when the form is saved.
    :cancel-mutation - Required. A *mutation* that will be transacted if the cancel button is pressed.
    :id - Optional. If not supplied will create a new instance. If supplied it will load and edit it.
    :save-params - Optional. Extra parameters (beyond the `:ident` that is auto-included) to pass to the save-mutation`
    :cancel-params - Optional. Parameters to pass to the cancel-mutation`

    Example usage:

    ```
    (defmutation saved [{:keys [ident]}]
      (action [{:keys [state]}]
        (swap! state update-in [:component/id ::Container] assoc
          :ui/selected-account ident
          :ui/open? false)))

    (defmutation cancel [_]
      (action [{:keys [state]}]
        (swap! state update-in [:component/id ::Container] assoc
          :ui/open? false)))

    (defsc Container [this {:ui/keys [open? selected-account edit-id] :as props}]
      {:query         [:ui/open? :ui/selected-account]
       :ident         (fn [] [:component/id ::Container])
       :initial-state {}}
      (comp/fragment {}
        (when open?
          (ui-form-modal {:Form            BriefAccountForm
                          :save-mutation   saved
                          :cancel-mutation cancel}))
        (dom/div (str selected-account))
        (dom/button {:onClick (fn []
                                (comp/transact! this [(m/set-props {:ui/open?   true})]))} \"New\")))

        ```
  "
  (comp/factory FormModal))
