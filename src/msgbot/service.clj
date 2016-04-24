(ns msgbot.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :as ring-resp]
            [clj-http.client :as client]
            [clojure.java.io :refer [resource]]
            [cheshire.core :refer [generate-string parse-string]]))

(def page-token "page-token")

(defn home-page [request] (ring-resp/response (slurp (resource "index.html"))))

(defn get-json-string [params] (generate-string params))

(defn json-->clojure [params] (parse-string params))

(defn- send-message [sender msg-data]
  (let [post-url (str "https://graph.facebook.com/v2.6/me/messages?access_token="page-token)
        json-params (get-json-string {:recipient {:id sender} :message msg-data})]
    (client/post post-url {:body json-params :content-type :json})))

(defn send-text-message [sender text]
  (send-message sender {:text text}))

(defn send-generic-message [sender]
  (send-message sender {:attachment {:type "template"
                                     :payload {:template_type "generic"
                                               :elements [{:title "Yeah!! did it" :subtitle "curejoy page"
                                                           :image_url "https://d1qb2nb5cznatu.cloudfront.net/startups/i/408540-8e7801b9834b164ed6e606895c893c33-medium_jpg.jpg?buster=1401433001"
                                                           :buttons [{:type "web_url" :url "https://www.curejoy.com/" :title "curejoy page"}
                                                                     {:type "postback" :title "ask again"
                                                                      :payload "we received your req"}]}
                                                          {:title "another one" :subtitle ""
                                                           :image_url "http://messengerdemo.parseapp.com/img/gearvr.png"
                                                           :buttons [{:type "postback" :title "Postback"
                                                                      :payload "Payload for second element in a generic bubble"}]}]}}}))


;;/callback?hub.mode=subscribe&hub.challenge=624684006&hub.verify_token=123
(defn callback [request]
  (let [params (:params request)]
    (if (= "123" (:hub.verify_token params))
  (ring-resp/response (:hub.challenge params))
      (ring-resp/response "Error..!!"))))


(defn receive-msg [request]
  (println request)
  (let [params (:json-params request)
        messaging-events (:messaging (first (:entry params)))
        message-event-handler  (fn [m] (let [sender (:id (:sender m))
                                             message (:message m)
                                             text (:text message)]
                                         (if (and message text)
                                           (if (= "generic" text)
                                             (send-generic-message sender)
                                             (send-text-message sender (str "Text received, echo: " text))))
                                         (if-let [postback (:postback m)]
                                           (send-text-message sender (str "Postback received: " postback)))))]
    (mapv message-event-handler messaging-events)
    (ring-resp/response "ok")))


(defroutes routes
  ;; Defines "/" and "/about" routes with their associated :get handlers.
  ;; The interceptors defined after the verb map (e.g., {:get home-page}
  ;; apply to / and its children (/about).
  [[["/" {:get home-page}
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/callback" {:get callback
                    :post receive-msg}]]]])

;; Consumed by msgbot.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::bootstrap/type :tomcat
              ;;::bootstrap/host "localhost"
              ::bootstrap/port 8080
              ;;::bootstrap/container-options{:ssl? true :ssl-port 9443 :keystore "file.keystore" :key-password ""}
              ;;(options for jetty ssl configuration)
              })
