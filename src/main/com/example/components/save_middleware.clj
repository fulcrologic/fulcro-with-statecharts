(ns com.example.components.save-middleware
  (:require
    [com.example.model-rad.model :as model]
    [com.fulcrologic.rad.blob :as blob]
    [com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]))

(defn wrap-exceptions-as-form-errors
  ([handler]
   (fn [pathom-env]
     (try
       (let [handler-result (handler pathom-env)]
         handler-result)
       (catch Throwable t
         {:com.fulcrologic.rad.form/errors [{:message (str "Unexpected error saving form: " (ex-message t))}]})))))

(def middleware
  (->
    (datomic/wrap-datomic-save)
    (wrap-exceptions-as-form-errors)
    (blob/wrap-persist-images model/all-attributes)
    (r.s.middleware/wrap-rewrite-values)))
