(ns memento.routes.api
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [compojure.api.meta :refer [restructure-param]]
            [compojure.api.sweet :refer [defapi context POST GET PUT DELETE]]
            [memento.middleware :refer [token-auth-mw]]
            [memento.routes.api.auth :as auth]
            [memento.routes.api.common :refer [read-content]]
            [memento.routes.api.memory :as memory]
            [memento.routes.api.reminder :as reminder]
            [numergent.utils :as utils]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (java.util UUID Date)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Access handlers and wrappers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn access-error [_ _]
  (unauthorized {:error "unauthorized"}))

(defn wrap-restricted [handler rule]
  (restrict handler {:handler  rule
                     :on-error access-error}))

(defmethod restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middleware] conj [wrap-restricted rule]))

(defmethod restructure-param :auth-data
  [_ binding acc]
  (update-in acc [:letks] into [binding `(:identity ~'+compojure-api-request+)]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Services
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(s/defschema User
  {:id    String
   :email String})

(s/defschema Thought
  {:id                         s/Uuid
   :username                   s/Str
   :thought                    s/Str
   :created                    s/Inst
   (s/optional-key :root_id)   (s/maybe s/Uuid)
   (s/optional-key :refine_id) (s/maybe s/Uuid)
   (s/optional-key :status)    s/Keyword})

(s/defschema ThoughtSearchResult
  {:total        s/Int
   :pages        s/Int
   :current-page s/Int
   :results      [Thought]})

(s/defschema ThreadResult
  {:id      s/Uuid
   :results [Thought]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Services
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defapi service-routes
  {:swagger {:ui   "/swagger-ui"
             :spec "/swagger.json"
             :data {:info {:version     "1.0.0"
                           :title       "Memento API"
                           :description "Signup and data access"}}}}

  (context "/api/auth" []
    :tags ["AUTH"]

    (POST "/login" []
      :return s/Str
      :body-params [username :- s/Str
                    password :- s/Str]
      :summary "Attempts to validate a username and password, and returns a token"
      (auth/login username password))

    (GET "/validate" []
      :return s/Str
      :header-params [authorization :- String]
      :middleware [token-auth-mw]
      :auth-rules authenticated?
      :auth-data auth-data
      :summary "Attempts to validate a token, and echoes it if valid"
      ;; You'll notice I don't actually do any validation here. This is
      ;; because the validation and the authentication verification are
      ;; the same. If we got this far, the token is valid.
      (ok (:token auth-data)))

    (POST "/signup" []
      :return s/Str
      :body-params [username :- s/Str
                    password :- s/Str
                    {password2 :- s/Str ""}]
      :summary "Creates a new user"
      ;; Returns an authentication token
      (auth/signup! username password)))

  (context "/api" []
    :tags ["THOUGHTS" "MEMORY"]

    ;; You'll need to be authenticated for these
    :middleware [token-auth-mw]
    :auth-rules authenticated?
    :header-params [authorization :- s/Str]


    (GET "/search" []
      :summary "Searches the thoughts"
      :query-params [{q :- s/Str ""}
                     {page :- s/Int 0}]
      :auth-data auth-data
      (memory/search-thoughts (:username auth-data) q page))

    (GET "/thoughts" []
      :summary "Gets the first page of thoughts"
      :return ThoughtSearchResult
      :query-params [{page :- s/Int 0}]
      :auth-data auth-data
      (memory/get-thoughts (:username auth-data) page))

    (POST "/thoughts" []
      :summary "Creates a new thought"
      :return Thought
      :body-params [thought :- s/Str
                    {refine_id :- (s/maybe s/Uuid) nil}]
      :auth-data auth-data
      (memory/save-thought (:username auth-data) thought refine_id))

    (PUT "/thoughts/:id" []
      :summary "Updates an existing thought. Needs to be open."
      :return Thought
      :path-params [id :- s/Uuid]
      :body-params [thought :- s/Str]
      :auth-data auth-data
      (memory/update-thought (:username auth-data) id thought))

    (DELETE "/thoughts/:id" []
      :summary "Deletes an existing thought. Needs to be open."
      :path-params [id :- s/Uuid]
      :auth-data auth-data
      (memory/delete-thought (:username auth-data) id))

    (GET "/threads/:id" []
      :summary "Gets a thread"
      :return ThreadResult
      :path-params [id :- s/Uuid]
      :auth-data auth-data
      (memory/get-thread (:username auth-data) id))
    )

  )