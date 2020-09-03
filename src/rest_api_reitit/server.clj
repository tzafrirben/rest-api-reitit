(ns rest-api-reitit.server
  (:require [clojure.string :as string]
            [muuntaja.core :as m]
            [malli.util :as mu]
            [reitit.ring :as ring]
            [reitit.coercion.malli]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.adapter.jetty :as jetty]
            [rest-api-reitit.middleware :as middleware]))

;;; users "data-model": for simplicity just hold collection of users in an atom
;;; so we can "add", "remove" and "update"" users in the collection
(def users (atom {1 {:user-id 1 :user-name "user-1" :first-name "one" :last-name "first"}
                  2 {:user-id 2 :user-name "user-2" :first-name "two" :last-name "second"}}))

(def next-user-id (atom (count @users)))

(def app
  (ring/ring-handler
   (ring/router
    [["/_/swagger.json"
      {:get {:no-doc  true
             :swagger {:info {:title       "Letter case REST API example"
                              :description "Using \"camelCase\" for REST API schema and \"kebab-case\" for back-end code"}
                       :tags [{:name "users" :description "Manage system users"}]}
             :handler (middleware/letter-case-swagger-response
                       (swagger/create-swagger-handler)
                       {:to :camelCase})}}]

     ["/v1"
      ["/users"
       {:swagger {:tags ["users"]}
        :get     {:summary    "List all users (or optionally filter using last name)"
                  :parameters {:query
                               [:map [:last-name {:optional true} string?]]}
                  :responses  {200 {:body [:map
                                           [:users
                                            [:or
                                             [:sequential [:map
                                                           [:user-id pos-int?]
                                                           [:user-name string?]
                                                           [:first-name string?]
                                                           [:last-name string?]]]
                                             empty?]]]}}
                  :handler    (fn [{{{:keys [last-name]} :query} :parameters}]
                                (if (some? last-name)
                                  {:status 200 :body {:users (->> @users
                                                                  vals
                                                                  (filter #(string/starts-with?
                                                                            (:last-name %)
                                                                            last-name)))}}
                                  {:status 200 :body {:users (vals @users)}}))}
        :post    {:summary    "Add new user"
                  :parameters {:body [:map
                                      [:user-name string?]
                                      [:first-name string?]
                                      [:last-name string?]]}
                  :responses  {201 {:body [:map
                                           [:user
                                            [:map
                                             [:user-id pos-int?]
                                             [:user-name string?]
                                             [:first-name string?]
                                             [:last-name string?]]]]}
                               400 {:body [:map [:error string?]]}}
                  :handler    (fn [{{{:keys [user-name] :as user} :body} :parameters}]
                                (let [user-exists? (->> (vals @users)
                                                        (filter #(= (:user-name %) user-name))
                                                        (seq)
                                                        (some?))]
                                  (if user-exists?
                                    {:status 400 :body {:error "User already exists"}}
                                    (let [user-id  (swap! next-user-id inc)
                                          new-user (assoc user :user-id user-id)]
                                      (swap! users assoc user-id new-user)
                                      {:status 201 :body {:user new-user}}))))}}]

      ["/users/{user-id}"
       {:swagger {:tags ["users"]}
        :get     {:summary    "Retrieve a user"
                  :parameters {:path [:map
                                      [:user-id pos-int?]]}
                  :responses  {200 {:body [:map
                                           [:user
                                            [:map
                                             [:user-id pos-int?]
                                             [:user-name string?]
                                             [:first-name string?]
                                             [:last-name string?]]]]}
                               400 {:body [:map [:error string?]]}}
                  :handler    (fn [{{{:keys [user-id]} :path} :parameters}]
                                (if-let [user (get @users user-id)]
                                  {:status 200 :body {:user user}}
                                  {:status 400 :body {:error "User does not exists"}}))}
        :put     {:summary    "Update a user"
                  :parameters {:path [:map
                                      [:user-id pos-int?]]
                               :body [:map
                                      [:user-name string?]
                                      [:first-name string?]
                                      [:last-name string?]]}
                  :responses  {200 {:body [:map
                                           [:user
                                            [:map
                                             [:user-id pos-int?]
                                             [:user-name string?]
                                             [:first-name string?]
                                             [:last-name string?]]]]}
                               400 {:body [:map [:error string?]]}}
                  :handler    (fn [{{:keys [body] {:keys [user-id]} :path} :parameters}]
                                (if-let [user (get @users user-id)]
                                  (let [updated-user (merge user body)]
                                    (swap! users assoc user-id updated-user)
                                    {:status 200 :body {:user updated-user}})
                                  {:status 400 :body {:error "User does not exists"}}))}
        :delete  {:summary    "Delete a user"
                  :parameters {:path [:map
                                      [:user-id pos-int?]]}
                  :responses  {200 {:body [:map
                                           [:user
                                            [:map
                                             [:user-id pos-int?]
                                             [:user-name string?]
                                             [:first-name string?]
                                             [:last-name string?]]]]}
                               400 {:body [:map [:error string?]]}}
                  :handler    (fn [{{{:keys [user-id]} :path} :parameters}]
                                (if-let [user (get @users user-id)]
                                  (do
                                    (swap! users dissoc user-id)
                                    {:status 200 :body {:user user}})
                                  {:status 400 :body {:error "User does not exists"}}))}}]]]

    {:data {:coercion   (reitit.coercion.malli/create
                         {:error-keys       #{:value :humanized}
                          :compile          mu/closed-schema
                          :strip-extra-keys false})
            :muuntaja   m/instance
            :middleware [; query-params & form-params
                         parameters/parameters-middleware
                         ; content-negotiation
                         muuntaja/format-negotiate-middleware
                         ; encoding response body
                         muuntaja/format-response-middleware
                         ; coerce response body to keys to camelCase
                         [middleware/letter-case-response {:to :camelCase}]
                         ; exception handling
                         exception/exception-middleware
                         ; decoding request body
                         muuntaja/format-request-middleware
                         ; coerce request params letter case
                         [middleware/letter-case-request {:from :camelCase :to :kebab-case}]
                         ; coercing response body
                         coercion/coerce-response-middleware
                         ; coercing request parameters
                         coercion/coerce-request-middleware]}})

   (ring/routes
    (ring/redirect-trailing-slash-handler {:method :strip})

    (swagger-ui/create-swagger-ui-handler
     {:path   "/api-docs"
      :url    "/_/swagger.json"
      :config {:validatorUrl     nil
               :operationsSorter "alpha"}})

    (ring/create-default-handler
     {:not-found          (constantly {:status 404, :body "not-found"})
      :method-not-allowed (constantly {:status 405, :body "not-allowed"})
      :not-acceptable     (constantly {:status 406, :body "not-acceptable"})}))))

;;; http server state (start\stop)
(defonce http-server (atom nil))

(defn start []
  (reset! http-server (jetty/run-jetty #'app {:port 3000 :join? false})))

(defn stop []
  (when-not (nil? @http-server)
    (.stop @http-server)))

(comment
  (start))
