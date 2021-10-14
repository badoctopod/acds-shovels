(defproject acds_shovels "2.0.0"
  :description "ACDS: API endpoint for shovel's HTTP client"

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [cprop "0.1.18"]
                 [seancorfield/next.jdbc "1.2.659"]
                 [com.oracle.jdbc/ojdbc8 "12.2.0.1"]
                 [com.zaxxer/HikariCP "4.0.3"]
                 [com.layerware/hugsql "0.5.1"]
                 [com.layerware/hugsql-adapter-next-jdbc "0.5.1"]
                 [http-kit "2.5.3"]
                 [javax.servlet/javax.servlet-api "4.0.1"]
                 [metosin/reitit "0.5.15"]
                 [org.clojure/data.xml "0.0.8"]
                 [com.taoensso/timbre "5.1.2"]
                 [org.slf4j/slf4j-api "1.7.32"]
                 [com.fzakaria/slf4j-timbre "0.3.21"]
                 [commons-daemon/commons-daemon "1.2.4"]]

  :repositories [["XWiki External Repository" "https://maven.xwiki.org/externals/"]]

  :pedantic? false

  :jvm-opts ["-Xmx2g"
             "-server"
             "-Dconf=config.edn"
             "-Duser.timezone=Europe/Moscow"]

  :plugins [[lein-ancient "0.6.15"]
            [lein-kibit "0.1.8"]]

  :min-lein-version "2.8.1"

  :source-paths ["src/clj"]

  :clean-targets ^{:protect false} [:target-path :compile-path]

  :main acds-shovels.core

  :profiles {:dev     {:prep-tasks   ["clean"]}
             :uberjar {:uberjar-name "acds-shovels.jar"
                       :source-paths ^:replace ["src/clj"]
                       :prep-tasks   ["compile"]
                       :hooks        []
                       :omit-source  true
                       :aot          :all}})
