(ns acds-shovels.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.data.xml :as xml]
   [spec-tools.core :as st]
   [cprop.core :refer [load-config]]
   [taoensso.timbre :as timbre]
   [taoensso.timbre.appenders.3rd-party.rotor :as appender]
   [hugsql.core :as hugsql]
   [hugsql.adapter.next-jdbc :as next-adapter]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [next.jdbc.result-set :as rs]
   [org.httpkit.server :refer [run-server]]
   [reitit.ring :as ring]
   [reitit.http :as http]
   [reitit.coercion.spec]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.http.coercion :as coercion]
   [reitit.dev.pretty :as pretty]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.http.interceptors.exception :as exception]
   [reitit.http.interceptors.multipart :as multipart]
   [muuntaja.core :as m])
  (:import
   [com.zaxxer.hikari HikariDataSource]
   [org.apache.commons.daemon Daemon DaemonContext])
  (:gen-class
   :implements [org.apache.commons.daemon.Daemon]
   :methods [^{:static true} [start ["[Ljava.lang.String;"] void]
             ^{:static true} [stop ["[Ljava.lang.String;"] void]]))

(let [edn-config (load-config)
      port (Integer. (or (System/getenv "PORT")
                         (get-in edn-config [:http :port])))]
  (def config (assoc-in edn-config [:http :port] port)))

(hugsql/def-db-fns "sql/queries.sql" {:quoting :ansi})

(defn proper-date?
  [s]
  (try
    (let [format (java.time.format.DateTimeFormatter/ofPattern "dd.MM.yyyy HH:mm:ss")]
      (java.time.LocalDate/parse s format)
      true)
    (catch java.time.format.DateTimeParseException _
      false)))

(s/def ::date
  (st/spec
   {:spec #(proper-date? %)
    :name "date"
    :description "Date in the format of dd.MM.yyyy HH:mm:ss"
    :reason "Illegal date format, use dd.MM.yyyy HH:mm:ss, example 12.08.2021 05:00:00"
    :swagger/example "12.08.2021 05:00:00"
    :swagger/default "12.08.2021 05:00:00"
    :json-schema/default "12.08.2021 05:00:00"}))

(s/def ::controlid
  (st/spec
   {:spec pos-int?
    :name "controlid"
    :description "iPan control identificator"
    :reason "Illegal control identificator, use positive integer, example 118"
    :swagger/example "118"
    :swagger/default "118"
    :json-schema/default "118"}))

(s/def ::shovcontrolid ::controlid)
(s/def ::utcfrom ::date)
(s/def ::utcto ::date)

(defn as-kebab-maps [rs opts]
  (let [kebab #(str/lower-case (str/replace % #"_" "-"))]
    (rs/as-unqualified-modified-maps rs (assoc opts :label-fn kebab))))

(defonce datasource (atom nil))

(defn start-connection-pool!
  [{:keys [pite]}]
  (let [ds (connection/->pool HikariDataSource pite)]
    (reset! datasource ds)
    (jdbc/execute! @datasource ["SELECT 1 FROM dual"])))

(defn stop-connection-pool!
  []
  (.close @datasource))

(def log-interceptor
  {:name ::log-interceptor
   :enter (fn [ctx]
            (let [start-ms (System/currentTimeMillis)
                  new-ctx (assoc-in ctx [:request :start-ms] start-ms)]
              (timbre/info
               (select-keys (:request new-ctx) [:server-name
                                                :server-port
                                                :remote-addr
                                                :uri
                                                :query-string
                                                :request-method
                                                :headers]))
              new-ctx))
   :leave (fn [ctx]
            (let [duration-ms (- (System/currentTimeMillis)
                                 (get-in ctx [:request :start-ms]))
                  new-ctx (assoc-in ctx [:response :duration-ms] duration-ms)]
              (timbre/info
               [(get-in new-ctx [:request :query-params])
                (dissoc (:response new-ctx) :body)])
              new-ctx))})

(defn trips-history->xml-str
  [{:keys [shovcontrolid utcfrom utcto minloadtime idlestoptype]}]
  (->> (map (fn [{:keys [truckname utcloadstart utcloadend
                         utcunload weightload weightunload]}]
              [:Trip
               {:truckName truckname
                :utcLoadStart utcloadstart
                :utcLoadEnd utcloadend
                :utcUnload utcunload
                :weightLoad weightload
                :weightUnload weightunload}])
            (get-trips-history @datasource {:shovcontrolid shovcontrolid
                                            :utcfrom utcfrom
                                            :utcto utcto
                                            :minloadtime minloadtime
                                            :idlestoptype idlestoptype}))
       (into [:TripsByShovHistory
              {:shovControlId shovcontrolid
               :utcFrom utcfrom
               :utcTo utcto}])
       (xml/sexp-as-element)
       (xml/emit-str)))

(def ring-handler
  (http/ring-handler
   (http/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger (let [swagger (get-in config [:api-description :swagger])
                            version (str (get-in config [:api-instance :instance])
                                         ":"
                                         (get-in config [:api-instance :version]))]
                        (-> swagger
                            (update-in [:info :version] (constantly version))))
             :handler (swagger/create-swagger-handler)}}]
     ["/soap/"
      {:get {:swagger {:tags ["shovels"]}
             :summary (get-in config [:api-description :soap :summary])
             :description (get-in config [:api-description :soap :description])
             :parameters {:query {:shovControlId ::shovcontrolid
                                  :utcFrom ::utcfrom
                                  :utcTo ::utcto}}
             :responses {200 {:body string?}}
             :handler (fn [{{{:keys [shovControlId utcFrom utcTo]} :query} :parameters}]
                        #(future %)
                        {:status 200
                         :headers {"Content-Type" "application/xml"}
                         :body (trips-history->xml-str {:shovcontrolid shovControlId
                                                        :utcfrom utcFrom
                                                        :utcto utcTo
                                                        :minloadtime (get-in config [:params :minloadtime])
                                                        :idlestoptype (get-in config [:params :idlestoptype])})})}}]]
    {:exception pretty/exception
     :data {:coercion reitit.coercion.spec/coercion
            :muuntaja (m/create
                       (-> m/default-options
                           (update
                            :formats
                            select-keys
                            ["application/json"
                             "application/edn"])
                           (assoc :default-format "application/json")))
            :interceptors [swagger/swagger-feature
                           log-interceptor
                           (parameters/parameters-interceptor)
                           (muuntaja/format-negotiate-interceptor)
                           (muuntaja/format-response-interceptor)
                           (exception/exception-interceptor)
                           (muuntaja/format-request-interceptor)
                           (coercion/coerce-response-interceptor)
                           (coercion/coerce-request-interceptor)
                           (multipart/multipart-interceptor)]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :operationsSorter "alpha"
               :displayRequestDuration true
               :docExpansion "list"
               :filter true
               :showExtensions true
               :showCommonExtensions true}})
    (ring/create-default-handler))
   {:executor sieppari/executor}))

(def webserver (atom nil))

(defn stop-web-server!
  [{:keys [http-misc]}]
  (when-not (nil? @webserver)
    (@webserver :timeout (:stop-timeout http-misc))
    (reset! webserver nil)))

(defn start-web-server!
  [{:keys [http]}]
  (reset! webserver (run-server #'ring-handler http)))

(defn configure-logging-backend
  [{:keys [log]}]
  (timbre/merge-config!
   {:min-level      (:level log)
    :timestamp-opts {:pattern (:pattern log)
                     :timezone (java.util.TimeZone/getTimeZone (:timezone log))}
    :appenders      {:rolling (appender/rotor-appender
                               {:path      (:path log)
                                :file-size (:file-size log)
                                :backlog   (:backlog log)})}}))

(defn start
  []
  (configure-logging-backend config)
  (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc
                        {:builder-fn as-kebab-maps}))
  (timbre/infof "[APP] - Start initiated...")
  (try
    (start-connection-pool! config)
    (catch Exception e
      (timbre/errorf "Error starting DB connection pool: %s" (.getMessage e))))
  (timbre/infof "[WEBSERVER] - Starting...")
  (start-web-server! config)
  (timbre/infof "[WEBSERVER] - Start completed: %s" (str (:http config))))

(defn stop
  []
  (timbre/infof "[APP] - Shutdown initiated...")
  (stop-web-server! config)
  (timbre/infof "[WEBSERVER] - Shutdown completed...")
  (try
    (stop-connection-pool!)
    (catch Exception _
      (timbre/warnf "DB connection pool hasn't been started, nothing to stop")))
  (shutdown-agents))

(defn -start
  [this]
  (start))

(defn -stop
  [this]
  (stop))

(defn -main
  [& args]
  (start))
