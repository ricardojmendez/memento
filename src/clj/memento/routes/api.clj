(ns memento.routes.api
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [compojure.api.meta :refer [restructure-param]]
            [compojure.api.sweet :refer [defapi context PATCH POST GET PUT DELETE]]
            [memento.middleware :refer [token-auth-mw]]
            [memento.routes.api.auth :as auth]
            [memento.routes.api.memory :as memory]
            [memento.routes.api.reminder :as reminder]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

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
  (update-in acc [:middleware] conj [`wrap-restricted rule]))

(defmethod restructure-param :auth-data
  [_ binding acc]
  (update-in acc [:letks] into [binding `(:identity ~'+compojure-api-request+)]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Services
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(s/defschema Reminder
  {:id                        s/Uuid
   :type_id                   s/Str
   :thought_id                s/Uuid
   :created                   s/Inst
   :next_date                 (s/maybe s/Inst)
   :properties                s/Any
   (s/optional-key :username) s/Str
   (s/optional-key :thought)  s/Str                         ; Returned when querying for pending reminders
   })

(s/defschema Thought
  {:id                         s/Uuid
   :username                   s/Str
   :thought                    s/Str
   :created                    s/Inst
   :archived?                  s/Bool
   (s/optional-key :root_id)   (s/maybe s/Uuid)
   (s/optional-key :refine_id) (s/maybe s/Uuid)
   (s/optional-key :status)    s/Keyword
   (s/optional-key :reminders) [Reminder]
   })

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
    :tags ["THOUGHTS"]

    ;; You'll need to be authenticated for these
    :middleware [token-auth-mw]
    :auth-rules authenticated?
    :header-params [authorization :- s/Str]


    (GET "/search" []
      :summary "Searches the thoughts"
      :query-params [{q :- s/Str ""}
                     {page :- s/Int 0}]
      :auth-data auth-data
      (memory/query-thoughts (:username auth-data) q page))

    (GET "/thoughts" []
      :summary "Gets the first page of thoughts"
      :return ThoughtSearchResult
      :query-params [{page :- s/Int 0}]
      :auth-data auth-data
      (memory/query-thoughts (:username auth-data) nil page))

    (GET "/thoughts/:id" []
      :summary "Gets a thought"
      :path-params [id :- s/Uuid]
      :return Thought
      :auth-data auth-data
      (memory/get-thought (:username auth-data) id))

    (POST "/thoughts" []
      :summary "Creates a new thought"
      :return Thought
      :body-params [thought :- s/Str
                    {refine_id :- (s/maybe s/Uuid) nil}]
      :auth-data auth-data
      (memory/save-thought (:username auth-data) thought refine_id))

    (PATCH "/thoughts/:id" []
      :summary "Updates an existing thought. Needs to be open."
      ;; I'm not 100% sure if patch should return a value, according to the standard, but
      ;; doing so here because it simplifies things if we have the full thought as it comes
      ;; from the server.
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

  (context "/api" []
    :tags ["REMINDERS"]

    ;; You'll need to be authenticated for these
    :middleware [token-auth-mw]
    :auth-rules authenticated?
    :header-params [authorization :- s/Str]

    (POST "/reminders" []
      :summary "Creates a new reminder for a thought"
      :return Reminder
      :body-params [thought-id :- s/Uuid
                    type-id :- s/Str]
      :auth-data auth-data
      (reminder/create-new (:username auth-data) thought-id type-id))

    (GET "/reminders/:id" []
      :summary "Retrieves a specific reminder by id"
      :return Reminder
      :path-params [id :- s/Uuid]
      :auth-data auth-data
      (reminder/get-reminder (:username auth-data) id))

    (PATCH "/reminders/:id" []
      :summary "Patches a reminder's next-date"
      :path-params [id :- s/Uuid]
      :body-params [next-date :- (s/maybe s/Inst)]
      :auth-data auth-data
      (reminder/set-next-date (:username auth-data) id next-date))

    (GET "/reminders" []
      :summary "Retrieves all pending reminders"
      :return [Reminder]
      :auth-data auth-data
      (reminder/get-pending-reminders (:username auth-data)))

    (POST "/reminders/viewed/:id" []
      :summary "Marks a reminder as viewed"
      :path-params [id :- s/Uuid]
      :return s/Int
      :auth-data auth-data
      (reminder/mark-as-viewed! (:username auth-data) id))
    )

  )